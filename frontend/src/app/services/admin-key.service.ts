import { Injectable } from '@angular/core';

const SESSION_KEY = 'qb_admin_key';

/** Manages the question-bank admin API key in sessionStorage.
 *  Session-scoped: cleared automatically when the tab/browser is closed. */
@Injectable({ providedIn: 'root' })
export class AdminKeyService {
  getKey(): string | null {
    return sessionStorage.getItem(SESSION_KEY);
  }

  setKey(key: string): void {
    sessionStorage.setItem(SESSION_KEY, key);
  }

  clearKey(): void {
    sessionStorage.removeItem(SESSION_KEY);
  }

  hasKey(): boolean {
    const k = this.getKey();
    return k !== null && k.length > 0;
  }
}
