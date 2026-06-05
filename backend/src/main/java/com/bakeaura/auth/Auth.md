Bakeaura — Auth Package Complete Summary (Final)

Package location
com.bakeaura.auth
What this package does in one line
Handles everything related to identity — who you are, proving it, and staying logged in securely.

File 1 — JwtUtil.java
What it is: The JWT toolbox. Single responsibility — JWT operations only.
Real world analogy: The ID card printing and reading machine at a company entrance.
Fields:

secretKey — secret ink used to sign tokens, read from application.properties
accessTokenExpirationMs — how long access token lives (15 minutes)
refreshTokenExpirationMs — how long refresh token lives (7 days)

Methods:
MethodWhat it doesgenerateAccessToken(Long userId, Role role)Builds JWT with userId as subject, role as claim, 15 min expiry, signs it, returns token stringgenerateRefreshToken(Long userId)Builds JWT with userId as subject, no role, 7 day expiry, signs it, returns token stringextractUserId(String token)Parses token, reads subject, converts String to Long, returns userIdextractRole(String token)Parses token, reads role claim, converts String back to Role enumisTokenValid(String token)Tries to parse token — returns true if signature genuine and not expired, false otherwiseisTokenExpired(String token)Reads expiry from token, checks if it is before current timeisAccessToken(String token)Checks tokenType claim equals "access"isRefreshToken(String token)Checks tokenType claim equals "refresh"extractAllClaims(String token)Decodes and returns entire token payloadgetSigningKey()Converts secret string to cryptographic HMAC key object, private method
How JWT is built — the builder pattern:
Jwts.builder()          → blank form
.subject(userId)        → fill: who owns this token
.claim("role", ...)     → fill: what is their role
.claim("tokenType",...) → fill: access or refresh
.issuedAt(...)          → fill: created at this time
.expiration(...)        → fill: expires at this time
.signWith(...)          → fill: stamp with secret ink
.compact()              → SUBMIT — produces final token string
How JWT is read — the parser pattern:
Jwts.parser()               → get a parser tool
.verifyWith(getSigningKey()) → use this key to verify stamp
.build()                    → lock configuration, parser ready
.parseSignedClaims(token)   → decode, verify stamp, check expiry — throws exception if anything wrong
.getPayload()               → get the data inside
.getSubject()               → read specific field
Key decisions:

Subject is Long userId not email — tokens stay valid even when user changes email
Role only in access token — refresh token has no role because role is fetched fresh from database at refresh time
tokenType claim exists so refresh tokens cannot be used as access tokens
.name() converts Role.CUSTOMER enum to String "CUSTOMER" for storage in token
Role.valueOf() converts String "CUSTOMER" back to Role.CUSTOMER enum when reading


File 2 — RefreshTokenStore.java
What it is: Redis storage manager for refresh tokens.
Real world analogy: A coat check desk. Stores your coat (refresh token) under your number (userId). Verifies it when you return. Destroys it when you leave.
Where data lives: Redis in-memory store, NOT PostgreSQL.
Redis key format: "refresh-token:42" where 42 is the userId
Fields:

REFRESH_TOKEN_PREFIX — constant "refresh-token:" to namespace keys, static final means belongs to class not instance, never changes
redisTemplate — Spring's remote control for Redis operations
refreshTokenExpirationMs — expiry duration matching JWT refresh expiry

Methods:
MethodWhat it doesstore(Long userId, String refreshToken)Saves token in Redis with auto-expiry — Redis deletes it after 7 days automaticallymatches(Long userId, String refreshToken)Fetches stored token, compares with provided token, true only if identicalrevoke(Long userId)Deletes Redis entry entirely — token permanently deadkey(Long userId)Private helper — builds key string "refresh-token:42"
Why refreshToken.equals(storedToken) not storedToken.equals(refreshToken):
If token was already revoked, storedToken is null. Calling .equals() on null throws NullPointerException. Calling it on refreshToken which we know is not null is safe.
Why matches() exists — token rotation protection:
Every refresh issues a new token and stores it in Redis replacing the old one. If a hacker has an old stolen token, matches() compares it against the current token in Redis — they do not match — hacker is blocked. The stolen token becomes useless as soon as the legitimate user next uses the app.

File 3 — AuthService.java
What it is: The brain of authentication. All business logic lives here.
Real world analogy: The receptionist who registers new visitors, verifies returning ones, issues ID cards, and cancels them on exit.
Dependencies:

UserRepository — save and find users in PostgreSQL
PasswordEncoder — BCrypt hash and verify passwords
JwtUtil — generate and read tokens
RefreshTokenStore — store and revoke tokens in Redis

register flow:

Check email uniqueness — reject if already exists
Create User — name, email, BCrypt-hashed password, role=CUSTOMER, isActive=true
Save to database — ID gets assigned by PostgreSQL after this line
Generate access token with savedUser.getId() and role
Generate refresh token with savedUser.getId()
Store refresh token in Redis keyed by userId
Return AuthResponse with both tokens

login flow:

Find user by email — throw generic error if not found
BCrypt verify password — throw same generic error if wrong
Check isActive — reject if banned
Generate both tokens with user.getId()
Store refresh token in Redis
Return AuthResponse

refresh flow:

Validate token is genuine, not expired, is a refresh token
Extract userId from token
Find user by userId in database — role fetched fresh here
matches() check — verify token matches Redis entry
Check isActive
Generate new access token and new refresh token (rotation)
Store new refresh token in Redis — old one dead
Return AuthResponse

logout flow:

Validate token
Extract userId
revoke() — delete from Redis permanently

Key decisions:

Error always says "Invalid email or password" never "Email not found" — prevents attackers knowing which emails exist
Role fetched from database during refresh — admin role changes take effect immediately on next refresh
Refresh token rotated on every refresh — stolen token window shrinks to minutes not days
User gets tokens immediately after register — no need to login separately after registering


File 4 — AuthController.java
What it is: HTTP entry point. Receives requests, delegates to AuthService, returns responses.
Real world analogy: The front door that directs visitors to the right desk.
Base route: /api/auth (to be updated to /api/v1/auth in Step 6)
Endpoints:
MethodRouteDelegates toPOST/api/auth/registerauthService.register()POST/api/auth/loginauthService.login()POST/api/auth/refreshauthService.refresh()POST/api/auth/logoutauthService.logout()
@Valid annotation: Spring automatically validates all @NotBlank and @Email constraints before the method runs. Invalid requests are rejected before reaching AuthService — controller never even sees them.
Still missing:

GET /api/v1/auth/verify-email?token=abc — added in Step 2


File 5 — JwtAuthFilter.java
What it is: Security guard running on every single HTTP request before it reaches any controller.
Real world analogy: Guard at every door who checks your badge and writes your name in the visitor log.
Extends OncePerRequestFilter: Guaranteed to run exactly once per request — never twice, never zero times.
Step by step flow:

Read Authorization header
Check header exists and starts with "Bearer "
Cut off "Bearer " (7 characters) — get raw token string
Three checks — token valid AND is access token AND not already authenticated
Extract userId and role from token — no database call
Convert role to Spring Security format: Role.CUSTOMER → "ROLE_CUSTOMER"
Create UsernamePasswordAuthenticationToken(userId, null, authorities)
Store in SecurityContextHolder
Pass request forward via filterChain.doFilter()

Key decisions:

No database call — role read from token for performance, one less DB hit per request
Refresh tokens blocked — isAccessToken() check prevents using refresh token to authenticate
userId stored as principal — controllers read it with:

javaLong userId = (Long) SecurityContextHolder
.getContext()
.getAuthentication()
.getPrincipal();
What SecurityContextHolder is:
Thread-local visitor log. Each request runs on its own thread. Each thread has its own isolated security context. Request A's user never mixes with Request B's user even running simultaneously. Wiped clean automatically when request ends.

Files 6-10 — Request and Response objects
FileFieldsPurposeAuthResponse.javaaccessToken, refreshToken, tokenType, email, roleReturned to frontend after register/login/refreshRegisterRequest.javaname, email, passwordData sent by frontend to registerLoginRequest.javaemail, passwordData sent by frontend to loginLogoutRequest.javarefreshTokenRefresh token sent to revoke on logoutRefreshTokenRequest.javarefreshTokenRefresh token sent to get new access token
Why request objects exist instead of raw parameters:
Spring's @RequestBody maps incoming JSON to Java objects automatically. Also makes adding fields later easy — just add to the class without changing controller signature.
Why LogoutRequest needs the refresh token:
Access token expires in 15 minutes anyway — no need to explicitly kill it. Refresh token lives 7 days in Redis — must be explicitly deleted or a stolen token could generate new access tokens for 7 days after logout.

Complete token lifecycle
REGISTER / LOGIN:
Frontend → AuthController → AuthService
→ generates access token (15 min) + refresh token (7 days)
→ stores refresh token in Redis keyed by userId
→ returns both tokens to frontend

EVERY API REQUEST:
Frontend sends Authorization: Bearer <accessToken>
→ JwtAuthFilter intercepts
→ validates, extracts userId and role
→ stores in SecurityContextHolder
→ controller reads userId from SecurityContextHolder
→ no database call in filter

ACCESS TOKEN EXPIRES (every 15 min):
Axios interceptor catches 401 automatically
→ silently calls POST /api/auth/refresh
→ AuthService validates refresh token
→ fetches user from DB (fresh role)
→ matches() verifies token against Redis
→ generates new access token + new refresh token (rotation)
→ old refresh token dead, new one stored in Redis
→ Axios retries original request
→ user sees nothing, app works normally

LOGOUT:
Frontend → AuthController → AuthService
→ validates refresh token
→ revoke() deletes from Redis
→ stolen tokens permanently useless

SESSION ENDS (7 days inactivity):
Refresh token expires in Redis
→ next refresh attempt fails
→ Axios interceptor clears tokens
→ redirects to login page

Security model — honest assessment
ProtectionStatusPasswords BCrypt hashed✅Tokens signed with HMAC secret✅Access token short lived (15 min)✅Refresh token rotation on every refresh✅Logout explicitly revokes Redis entry✅Banned users blocked at refresh✅Refresh tokens cannot authenticate requests✅Generic error messages prevent email enumeration✅Full token family tracking❌ Not implemented — known gap
Known gap — stolen refresh token during inactivity:
If user does not open app for days and hacker has their current refresh token, hacker can generate access tokens freely until user next opens app and triggers rotation. Full solution is token family tracking. Not implemented in Bakeaura — acceptable for portfolio, must be explained confidently in interviews.
Interview answer:

"Bakeaura uses refresh token rotation — every refresh issues a new token and invalidates the old one. I am aware this does not fully protect against a stolen token during user inactivity. A complete solution would involve token family tracking where using an old token invalidates the entire family. I chose not to implement this to keep complexity manageable for a portfolio project."