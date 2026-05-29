import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useAuthStore = create(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      email: null,
      role: null,
      isAuthenticated: false,
      setAuth: (authData) =>
        set({
          accessToken: authData?.accessToken ?? null,
          refreshToken: authData?.refreshToken ?? null,
          email: authData?.email ?? null,
          role: authData?.role ?? null,
          isAuthenticated: Boolean(authData?.accessToken)
        }),
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          email: null,
          role: null,
          isAuthenticated: false
        })
    }),
    {
      name: 'bakeaura-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        email: state.email,
        role: state.role,
        isAuthenticated: state.isAuthenticated
      })
    }
  )
);
