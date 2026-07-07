import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useAuthStore = create(
    persist(
        (set) => ({
            accessToken: null,
            refreshToken: null,
            email: null,
            name: null,
            role: null,
            isAuthenticated: false,
            emailVerified: false,
            cartCount: 0,
            setAuth: (authData) =>
                set({
                    accessToken: authData?.accessToken ?? null,
                    refreshToken: authData?.refreshToken ?? null,
                    email: authData?.email ?? null,
                    name: authData?.name ?? null,
                    role: authData?.role ?? null,
                    isAuthenticated: Boolean(authData?.accessToken),
                    emailVerified: Boolean(authData?.emailVerified)
                }),
            logout: () =>
                set({
                    accessToken: null,
                    refreshToken: null,
                    email: null,
                    name: null,
                    role: null,
                    isAuthenticated: false,
                    emailVerified: false,
                    cartCount: 0
                }),
            setCartCount: (count) => set({ cartCount: count }),
            setName: (name) => set({ name })
        }),
        {
            name: 'bakeaura-auth',
            partialize: (state) => ({
                accessToken: state.accessToken,
                refreshToken: state.refreshToken,
                email: state.email,
                name: state.name,
                role: state.role,
                isAuthenticated: state.isAuthenticated,
                emailVerified: state.emailVerified
            })
        }
    )
);
