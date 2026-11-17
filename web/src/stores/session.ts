import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDeviceId } from '@/lib/api'

/** 会话级状态：匿名设备标识与昵称（MVP 无登录）。 */
export const useSessionStore = defineStore('session', () => {
  const deviceId = ref(getDeviceId())
  const displayName = ref('旁听者')

  function setDisplayName(name: string): void {
    displayName.value = name.trim() || '旁听者'
  }

  return { deviceId, displayName, setDisplayName }
})
