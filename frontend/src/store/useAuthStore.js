import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useAuthStore = create(
    persist(
        (set) => ({
            id: null,
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
                    id: authData?.id ?? null,
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
                    id: null,
                    accessToken: null,
                    refreshToken: null,
                    email: null,
                    name: null,
                    role: null,
                    isAuthenticated: false,
                    emailVerified: false,
                    cartCount: 0
                }),
            setEmailVerified: (val = true) => set({ emailVerified: Boolean(val) }),
            setCartCount: (count) => set({ cartCount: count }),
            setName: (name) => set({ name })
        }),
        {
            name: 'bakeaura-auth',
            partialize: (state) => ({
                id: state.id,
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







































/*
===========================================================
AUTH STORE (Zustand Global State)
===========================================================

Purpose:
- Stores the authenticated user's session globally.
- Maintains:
    • User ID
    • Name
    • Email
    • Role
    • Access Token
    • Refresh Token
    • Authentication Status
    • Email Verification Status
    • Cart Count
- Persists authentication data using localStorage.
- Provides actions to:
    • Login (setAuth)
    • Logout
    • Update Email Verification
    • Update Cart Count
    • Update User Name

Flow:
User Login →
Backend Returns Tokens →
setAuth() Updates Store →
Axios Reads Tokens From Store →
Authenticated API Requests

This file is the single source of truth for user authentication
throughout the frontend application.
===========================================================
*/