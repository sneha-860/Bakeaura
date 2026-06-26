Complete Guide to JWT, Spring Security, and Authentication for Bakeaura

Part 1: The Three Things People Always Confuse — Encoding, Encryption, Signing
These three words look similar but are completely different. You must separate them in your head before anything else makes sense.

Encoding — just a format change, zero security
Encoding converts data from one format to another so it can be transmitted or stored. There is no secret, no key, no protection. Anyone can reverse it.
Morse code is encoding. A = .- and anyone who knows Morse can decode it. Base64 is the same idea — it converts binary data into readable text characters. Anyone in the world can decode Base64 instantly. There is no key required.
This is exactly what happens to the header and payload of a JWT. They are Base64 encoded. Anyone can decode them and read the contents. This is why the JWT payload is not secret — it is just encoded, not protected.
You decode encoded data. That is the correct word — decode.

Encryption — locking data so only someone with the key can read it
Encryption converts readable data into unreadable scrambled data. To read it back, you need the key. Without the key, the scrambled data is useless.
Think of a locked box. You put a letter inside, lock it, and send it. Only the person with the matching key can open it and read the letter. AES and RSA are encryption algorithms.
JWT does NOT encrypt the payload. The payload is readable by anyone. This surprises most beginners, but it is intentional — the goal of JWT is not to hide the data, it is to prove the data was not tampered with. If you need to hide sensitive data, you simply do not put it in the JWT payload.
You decrypt encrypted data. That is the correct word — decrypt.

Signing — proving the data came from you and was not changed
Signing does not hide data and does not change its format. It only proves authenticity. Think of a wax seal on a royal letter in old times. The king presses his unique stamp into hot wax on the envelope. Anyone can open the envelope and read the letter. But if the seal is broken or faked, you know someone tampered with it. And nobody can fake the seal without the king's stamp.
JWT signing works exactly this way. The payload is readable by everyone. The signature just proves it came from your server and was not changed by anyone.
You verify a signature. That is the correct word — verify.

Summary of the three:
Encoding — anyone can reverse it, no key needed, just a format change, you decode it.
Encryption — only someone with the key can reverse it, data is hidden, you decrypt it.
Signing — data is not hidden, not changed in format, but a mathematical proof is attached that proves it is genuine and untampered, you verify it.
JWT uses encoding for the header and payload, and signing for the signature. It does not use encryption anywhere unless you specifically use something called JWE, which you are not using in Bakeaura.

Part 2: The Signing Key — What It Is and Why It Matters
The signing key is a long secret string that only your server knows. It lives in your application.properties or config file. Something like:
jwt.secret=bakeaura_super_secret_key_nobody_knows_this_2024_xyz
This key is never sent to the frontend. It never leaves the server. It is the most critical secret in your entire application because everything depends on it.
The signing key is used in exactly two moments:
Moment 1 — When generating a token after login:
You take the encoded header, add a dot, add the encoded payload, and run it through a mathematical function called HMAC-SHA256 along with your secret key. The output is your signature.
signature = HMAC_SHA256( encodedHeader + "." + encodedPayload, secretKey )
HMAC-SHA256 is a one-way function. Given the same inputs, it always produces the same output. But you cannot reverse it — you cannot figure out what went in by looking at what came out. And crucially, you cannot produce the correct output without the secret key.
Moment 2 — When validating a token on every future request:
You take the header and payload from the incoming token, run them through HMAC-SHA256 again with your secret key, and compare the output with the signature that came in the token.
If they match — the token is genuine, nothing was changed.
If they do not match — someone tampered with the token and you reject it.
This is why an attacker can decode the payload, read the email and role inside, even change them — but they cannot recreate the correct signature without your secret key. So your server rejects the tampered token immediately.
If someone steals your secret key, they can create valid tokens for any user with any role. This is why the secret key must be long, random, and stored securely — never hardcoded in code that goes to GitHub.

Part 3: What is Inside a JWT Token
A JWT looks like this:
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzbmVoYUBnbWFpbC5jb20iLCJyb2xlIjoiQ1VTVE9NRVIiLCJleHAiOjE3MjAwMDAwMDB9.xK2z9M_signature_here
Three parts separated by dots. Each part has a specific job.

Part 1 — Header
Base64 encoded. Decode it and you get:
json{ "alg": "HS256", "typ": "JWT" }
This just declares: I am a JWT, and I was signed using the HS256 algorithm. Nothing else.

Part 2 — Payload (also called Claims)
Base64 encoded. Decode it and you get:
json{
"sub": "sneha@gmail.com",
"role": "CUSTOMER",
"exp": 1720000000,
"iat": 1719996400
}
sub means subject — who this token belongs to. Usually the email or user ID.
role is your custom claim — the user's role in Bakeaura.
exp is expiry — a Unix timestamp (seconds since January 1, 1970). After this time the token is invalid. Your JwtUtil checks this automatically.
iat is issued-at — when the token was created.
These are readable by anyone. Do not put passwords, card numbers, or sensitive data here.

Part 3 — Signature
This is NOT Base64 encoded. It is the raw output of HMAC-SHA256 as described above. It is not human-readable and cannot be decoded — it can only be verified by running the same function again with the same secret key.

How to decode a JWT yourself:
In Java manually:
javaString[] parts = token.split("\\.");
String payloadJson = new String(Base64.getDecoder().decode(parts[1]));
System.out.println(payloadJson);
// Prints: {"sub":"sneha@gmail.com","role":"CUSTOMER","exp":1720000000}
Using your JJWT library (the proper way in your project):
javaClaims claims = Jwts.parserBuilder()
.setSigningKey(secretKey)
.build()
.parseClaimsJws(token)
.getBody();

String email = claims.getSubject();
String role  = claims.get("role", String.class);
Date expiry  = claims.getExpiration();
This one call does everything — splits the token, decodes the parts, recomputes the signature using your secret key, compares it, checks expiry, and returns the payload. If anything is wrong it throws an exception automatically.
For debugging during development, go to jwt.io, paste any token, and it shows you the decoded header and payload instantly. You can also enter your secret key there to verify the signature.

Part 4: HTTP is Stateless — What This Means
Stateless means the server has zero memory between requests. Every single request is treated as a complete stranger walking in for the first time. The server does not remember anything from the previous request.
The amnesia receptionist analogy:
Imagine a hotel receptionist who has complete amnesia every few seconds.
You walk up and say "I am Sneha, I want to check into room 101." She checks your ID, verifies it, gives you the key.
You walk back five minutes later and say "Can I get extra towels for room 101?" She looks at you blankly and says "Who are you? I have never seen you before."
That is HTTP. The server genuinely does not remember that you logged in five minutes ago. There is no automatic memory between requests.
How JWT solves this:
Now imagine the receptionist gives you a wristband after check-in. The wristband has your name, room number, and the hotel's official stamp on it. Every time you come back, you show the wristband. She does not need to remember you — everything is on the wristband, and the stamp proves it is real.
JWT is that wristband. After login, your server gives you a token. You store it in your frontend (localStorage or a cookie). On every subsequent request, your frontend automatically sends it in the Authorization header:
Authorization: Bearer eyJhbGci...
The server reads it, verifies it, and knows exactly who you are and what role you have — without storing anything on the server side.
The alternative — sessions (stateful, old-school way):
Old web apps used sessions. After login, the server stores your info in its own memory and gives you a session ID. On every request you send the session ID, the server looks it up in its memory and finds your info.
The problem: if you have multiple servers (load balancing), server 2 does not have server 1's memory. And storing millions of sessions uses a lot of server memory.
JWT is stateless — the server stores nothing. All the info is inside the token. This is why JWT is standard for REST APIs and scales well.

Part 5: Filters — What They Are and How They Work
What is a filter:
A filter is a piece of code that runs on every HTTP request before it reaches your controller. Think of it as a checkpoint. Every request must pass through every checkpoint every single time.
This is not a one-time thing. Every request, every time, no exceptions. Just like airport security — every passenger, every flight, every day goes through the metal detector. It does not remember that you passed yesterday.
The filter chain:
Filters are arranged in a chain. Request enters the chain, passes through each filter one by one in order, and finally reaches your controller. If any filter decides to block the request, it stops right there and sends back a response like 401 or 403. The request never reaches the controller.

Part 6: All the Filters in Spring Boot and Spring Security
Here are all the important filters in the order they run, explained simply.

Spring Boot filters — run before Spring Security even starts:
CharacterEncodingFilter — ensures that all request and response text is read as UTF-8. This just makes sure characters like Hindi text, emojis, or special symbols are not garbled. You never touch this filter but it runs on every request.
HiddenHttpMethodFilter — HTML forms can only send GET and POST requests. This filter lets old-school HTML forms fake a PUT or DELETE by including a hidden field. Not relevant for Bakeaura since your React frontend sends proper HTTP verbs directly.
FormContentFilter — allows PUT and PATCH requests to carry form data in the body. Again, mostly for HTML forms. Not relevant for your JSON-based REST API.

Spring Security filter chain — the important ones in order:
SecurityContextHolderFilter — this is the first and last important filter. At the start of every request it sets up an empty SecurityContext (the current-user notepad). At the end of every request it clears the SecurityContext completely. This is what makes each request start completely fresh with no memory of the previous one.
HeaderWriterFilter — automatically adds security-related HTTP response headers. Examples: X-Content-Type-Options prevents browsers from guessing file types. X-Frame-Options prevents your app from being embedded in an iframe on a malicious site. Strict-Transport-Security enforces HTTPS. You get all of this for free without writing any code.
CorsFilter — handles Cross-Origin Resource Sharing. Your React frontend runs on localhost:5173. Your Spring Boot backend runs on localhost:8080. Browsers block requests between different origins by default — this is a browser security rule called CORS. This filter checks if the incoming request's origin is in your allowed list, and if yes, adds the right headers to the response so the browser allows it. You configure allowed origins in your SecurityConfig.
LogoutFilter — watches for requests to the /logout URL. If detected, clears the SecurityContext and handles session invalidation. Since Bakeaura uses JWT and you handle logout on the frontend by simply deleting the token, this filter does nothing meaningful in your app.
JwtFilter — your custom filter, the most important one for your app — this is the filter you wrote. It runs on every request. It reads the Authorization header, extracts the token, validates it using your secret key, loads the user from the database, and sets the user's identity in the SecurityContext. Full detailed walkthrough is in the next part.
UsernamePasswordAuthenticationFilter — handles traditional form-based login where username and password are submitted as form fields. Since you use a custom /api/auth/login REST endpoint that accepts JSON and returns a JWT, this filter is essentially bypassed in your setup. Spring does not use it for your login flow.
ExceptionTranslationFilter — acts as an error catcher for security exceptions. If a request reaches a protected endpoint without a valid token, or if the user's role does not permit access, this filter catches the exception and converts it into a proper HTTP response. Missing or invalid token becomes 401 Unauthorized. Valid token but wrong role becomes 403 Forbidden. Without this filter, security exceptions would cause ugly 500 server errors instead of clean 401 and 403 responses.
AuthorizationFilter — the final gate. After all filters have run and the user identity has been set (or not set) in the SecurityContext, this filter checks the rules you defined in SecurityConfig. Does the current user have the required role to access this URL? If yes, the request proceeds to your controller. If no, it throws an exception which ExceptionTranslationFilter converts to 403.

The complete order for every single request:
Request arrives at your server
↓
CharacterEncodingFilter       → ensures UTF-8 text handling
↓
SecurityContextHolderFilter   → creates empty current-user notepad
↓
HeaderWriterFilter            → adds security headers to response
↓
CorsFilter                    → checks if frontend origin is allowed
↓
JwtFilter (your filter)       → validates JWT, sets user identity in notepad
↓
ExceptionTranslationFilter    → watches for security errors
↓
AuthorizationFilter           → checks role permissions for this URL
↓
Your Controller               → actual business logic runs here
↓
Response travels back up through the chain
↓
SecurityContextHolderFilter   → clears the notepad completely
↓
Response sent to frontend

Part 7: Your JwtFilter — Step by Step What Happens Inside It
Every request, this filter runs. Here is exactly what it does:
Step 1 — Read the Authorization header from the request. It should look like: Authorization: Bearer eyJhbGci...
Step 2 — Check if the header exists and starts with the word "Bearer ". If it does not exist or does not start with Bearer, skip the rest of this filter and move on. This handles public routes like /api/auth/login where no token is expected.
Step 3 — Extract just the token string — everything after "Bearer " (7 characters).
Step 4 — Call JwtUtil.extractEmail(token). This decodes the payload from Base64 and reads the sub field to find the email of the user this token belongs to.
Step 5 — Check if the SecurityContext already has an authenticated user. If yes, skip — this request is already authenticated somehow.
Step 6 — Load the user from your database using UserDetailsService.loadUserByUsername(email). This gives you the user's stored password hash and role.
Step 7 — Call JwtUtil.validateToken(token, userDetails). This recomputes the signature using your secret key, compares it with the signature in the token, and checks if the expiry time has passed.
Step 8 — If valid, create a UsernamePasswordAuthenticationToken object containing the user's details and role. Set this in the SecurityContextHolder. Now Spring Security knows who this request belongs to for the rest of this request's life.
Step 9 — Call filterChain.doFilter(request, response) — this passes the request to the next filter in the chain.
If the token is expired, tampered, or the user is not found, the filter either throws an exception or simply does not set anything in the SecurityContext. The AuthorizationFilter later sees an unauthenticated request to a protected route and returns 401.

Part 8: SecurityContextHolder — The Current-User Notepad
The SecurityContextHolder is a temporary storage that holds the identity of the currently authenticated user. It exists only for the duration of one single request. When the request ends, it is wiped clean.
Think of it as a sticky note on your desk. You write the current customer's name on it when they walk in. You use it throughout the conversation. The moment they leave, you throw the note away. Next customer, fresh note.
java// JwtFilter writes to it:
SecurityContextHolder.getContext().setAuthentication(authToken);

// Anywhere else in your code — service, controller — you can read from it:
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String email = auth.getName();
Because HTTP is stateless, this notepad starts empty on every request. The JwtFilter's entire job is to fill it in at the start of every request. Everything after that — your services, controllers, authorization checks — relies on what the JwtFilter wrote here.

Part 9: BCrypt — Password Hashing
When a user registers with password hello123, you never store hello123 in the database. You store a BCrypt hash like:
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
BCrypt is a one-way function. You can produce the hash from the password. You cannot produce the password from the hash. This is different from encoding (which is reversible) and different from encryption (which is reversible with a key). BCrypt is truly one-way — there is no reversing it.
When the user logs in, BCrypt hashes their input and compares the two hashes. If they match, the password is correct.
java// Registration
user.setPassword(passwordEncoder.encode("hello123"));

// Login — Spring does this automatically
passwordEncoder.matches("hello123", storedHash); // returns true or false
Even if your entire database is stolen, the attacker only has hashes. They cannot recover the original passwords from them.

Part 10: RBAC — Roles and How They Are Enforced
RBAC stands for Role-Based Access Control. Your four roles are CUSTOMER, SELLER, INFLUENCER, and ADMIN. Each role can access certain routes and not others.
The role is stored in your users table. When you generate a JWT after login, you put the role in the payload. When JwtFilter processes the token, it reads the role and registers it in the SecurityContext as an "authority" called ROLE_CUSTOMER or ROLE_ADMIN etc.
You enforce roles in two places:
In SecurityConfig — broad URL-level rules:
java.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/seller/**").hasRole("SELLER")
.requestMatchers("/api/auth/**").permitAll()
.anyRequest().authenticated()
In individual methods — fine-grained rules:
java@PreAuthorize("hasRole('SELLER')")
public ResponseEntity<?> addProduct(...) { }
If a CUSTOMER hits a SELLER-only endpoint, the AuthorizationFilter catches it and returns 403 Forbidden automatically. You write zero code for the rejection — Spring handles it.

Part 11: The Complete Journey of Every Request
Login request — no token yet:
POST /api/auth/login  { email, password }
→ SecurityConfig: /api/auth/** is permitAll → JWT check skipped
→ AuthController → AuthService
→ AuthService calls AuthenticationManager
→ AuthenticationManager calls UserDetailsService → loads user from DB
→ BCrypt verifies password matches stored hash
→ JwtUtil generates token with email and role in payload, signed with secret key
→ Token returned to frontend → frontend stores it
Any protected request — token present:
GET /api/orders/123
Authorization: Bearer eyJhbGci...
→ JwtFilter reads header → extracts token → decodes payload → gets email
→ Loads user from DB → recomputes signature → signature matches → not expired
→ Sets user identity in SecurityContextHolder
→ AuthorizationFilter: does CUSTOMER role have access to /api/orders/123? Yes
→ OrderController runs → fetches order → returns response
→ SecurityContext cleared
Tampered or expired token:
GET /api/orders/123
Authorization: Bearer eyJhbGci...TAMPERED
→ JwtFilter reads header → extracts token → recomputes signature
→ Signature does not match → validation fails
→ SecurityContext never gets set
→ AuthorizationFilter sees unauthenticated request to protected route
→ ExceptionTranslationFilter converts it to 401 Unauthorized
→ Response returned, controller never runs

The Master Checklist — Everything You Must Know
Before reading a single line of your project code, these are the concepts that must be clear:
HTTP is stateless — server remembers nothing between requests, every request is a stranger, JWT is the wristband that proves identity on every request.
Encoding vs Encryption vs Signing — encoding is reversible by anyone, encryption is reversible with a key, signing is a mathematical proof of authenticity. JWT uses encoding for header and payload, signing for the signature, no encryption.
Signing key — secret string only your server knows, used to create and verify the signature, if stolen anyone can forge tokens.
HMAC-SHA256 — the math function that takes header plus payload plus secret key and produces the signature.
JWT structure — three dot-separated parts, header declares algorithm, payload carries email and role and expiry, signature is the tamper-proof seal.
Filters — run on every request every time, arranged in a chain, each does one job, any filter can block the request.
JwtFilter — your custom filter, validates the token, sets user identity in SecurityContext.
SecurityContextHolder — temporary notepad for current user identity, created fresh every request, cleared after every request.
UserDetailsService — translates your User entity into Spring's format so Spring can work with it.
BCrypt — one-way password hashing, cannot be reversed, verified by hashing the input again and comparing.
RBAC — roles in the database, put in JWT payload, enforced by Spring via SecurityConfig rules and @PreAuthorize annotations.




Jwts.builder()                          // give me a blank form
.subject("42")                      // fill: owner of this token
.claim("role", "CUSTOMER")          // fill: their role
.claim("tokenType", "access")       // fill: what kind of token
.issuedAt(new Date())               // fill: issued right now
.expiration(new Date(...))          // fill: expires at this time
.signWith(getSigningKey())          // fill: stamp it with secret ink
.compact()                          // submit the form → get final token string



.compact() is the submit button.
Up until .compact(), you're just configuring what the token should look like. Nothing has actually been created yet. When you call .compact():

The payload gets Base64 encoded
The header gets Base64 encoded
Both get signed using your secret key
All three parts get joined with dots



String subject = Jwts.parser()
Get a parser tool — something that knows how to decode JWT strings.