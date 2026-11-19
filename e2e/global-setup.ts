/**
 * E2E 全局夹具：假模型 + 干净库 + 后端 + 前端，一条命令把整条链路拉起来。
 *
 * 为什么不用 playwright.config 的 webServer：
 * 1. 必须在后端启动**之前**删库，否则会删到正在被写的那份文件；
 * 2. 假模型要先于后端就绪，顺序可控才不会出现"第一次组局就 404"。
 *
 * 依赖：JDK 17 / Maven / pnpm 已在本机可用（与 README §5 的前置一致）。
 */
import { spawn, spawnSync, type ChildProcess } from 'node:child_process'
import { createWriteStream, mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { purge } from './clean.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..')
const TMP = join(HERE, '.tmp')
const DB = join(TMP, 'e2e.db')
const IS_WIN = process.platform === 'win32'

const API = 'http://127.0.0.1:8080'
const WEB = 'http://localhost:5173'
const MOCK = 'http://127.0.0.1:9099'

function log(name: string): NodeJS.WritableStream {
  return createWriteStream(join(TMP, `${name}.log`))
}

function run(name: string, command: string, args: string[], env: NodeJS.ProcessEnv = {}): ChildProcess {
  const child = spawn(command, args, {
    cwd: ROOT,
    shell: IS_WIN,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: !IS_WIN,
  })
  const out = log(name)
  child.stdout?.pipe(out)
  child.stderr?.pipe(out)
  child.on('exit', (code) => {
    if (code !== 0 && code !== null) {
      console.log(`[e2e] ${name} 退出码 ${code}，日志见 e2e/.tmp/${name}.log`)
    }
  })
  return child
}

async function waitFor(url: string, label: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) {
        return
      }
    } catch {
      // 还没起来，继续等
    }
    await new Promise((done) => setTimeout(done, 500))
  }
  throw new Error(`等待 ${label} 超时（${timeoutMs}ms）：${url}，日志见 e2e/.tmp/`)
}

function stop(child: ChildProcess | undefined): void {
  if (!child || child.killed || child.pid === undefined) {
    return
  }
  if (IS_WIN) {
    // 必须同步等待 taskkill 完成：异步 spawn 后 Playwright 立即退出，
    // 子进程（mvn 启动的 java / pnpm 启动的 vite）会残留并占住 e2e.db 与端口，
    // 下一次 pretest 删库就报 EPERM。
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
    return
  }
  try {
    process.kill(-child.pid, 'SIGTERM')
  } catch {
    child.kill('SIGTERM')
  }
}

export default async function globalSetup(): Promise<() => Promise<void>> {
  purge(TMP)
  mkdirSync(TMP, { recursive: true })

  const mock = run('mock-llm', process.execPath, [join(HERE, 'mock-llm.mjs')])
  await waitFor(`${MOCK}/v1/models`, '假模型服务', 15_000)

  const backend = run('server', 'mvn', ['-o', '-f', join(ROOT, 'server', 'pom.xml'), 'spring-boot:run'], {
    SERVER_PORT: '8080',
    SQLITE_PATH: DB,
    LLM_BASE_URL: `${MOCK}/v1`,
    LLM_API_KEY: 'e2e-mock-key',
    LLM_MODEL: 'mock-model',
    WEB_ORIGIN: WEB,
  })
  await waitFor(`${API}/api/health`, '后端', 240_000)

  const web = run('web', 'pnpm', ['-C', join(ROOT, 'web'), 'dev', '--port', '5173', '--strictPort'])
  await waitFor(WEB, '前端', 120_000)

  return async () => {
    stop(web)
    stop(backend)
    stop(mock)
  }
}
