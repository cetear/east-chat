import { shallowRef } from 'vue'
export interface Identity { token: string; roles: string[] }
declare global {
  interface Window { easychatIdentity?: Identity }
  interface WindowEventMap { 'easychat:identity': CustomEvent<Identity | null> }
}
// The existing identity system owns login and role verification. Tokens stay in memory.
export const identity = shallowRef<Identity | null>(window.easychatIdentity ?? null)
export const authRequired = import.meta.env.VITE_AUTH_ENABLED !== 'false'
export function setIdentity(value: Identity | null): void { identity.value = value }
window.addEventListener('easychat:identity', event => setIdentity(event.detail))
export function authorizationHeaders(): Record<string, string> {
  return identity.value?.token ? { Authorization: `Bearer ${identity.value.token}` } : {}
}
export function isAdmin(): boolean {
  return !authRequired || !!identity.value?.roles.includes('admin')
}
