# Bakeaura — Complete Project Summary & Interview Companion

> Written for Sneha. This document was produced by reading **every file** in the `backend/` Spring Boot project (160 main source files + 12 test files + all config/build files) and the `frontend/` React app's API layer and routing, on 2026-06-28. Where the code disagrees with older docs in this repo (`BAKEAURA_API_REFERENCE.md`, `BAKEAURA_FRONTEND_BUILD_PROMPT.md`, `JWT.md`), this document follows the **actual code**, and the disagreement is called out explicitly so you know which to trust.

---

## SECTION 1 — PROJECT OVERVIEW

### What is Bakeaura?

Bakeaura is a **local home-bakery marketplace**. Think "Swiggy/Zomato, but only for home bakers" with a social/discovery layer bolted on:

- **Customers** browse home bakers ("sellers") near them, order cakes/cookies/bread, pay online via Razorpay, and track the order live (WebSocket) from "Pending" to "Delivered."
- **Sellers** (home bakers) list products, manage a storefront profile (shop name, bio, delivery radius, open/closed toggle), fulfil orders, accept custom-cake requests, and post short video "Reels" of their baking to a ranked discovery feed.
- **Influencers** get a referral code, earn a 10% commission (credited to an internal wallet) whenever someone orders using their code, and can request payouts.
- **Admins** moderate the platform: approve/reject "become a Seller/Influencer" applications, activate/deactivate accounts, change roles, and see dashboard counts.

A user starts as `CUSTOMER` and **applies** to become `SELLER` or `INFLUENCER` — there's no separate signup form per role. An admin approves the application, and the backend auto-provisions the right profile rows (seller profile, influencer profile + referral code) the moment that happens. This is the single most important business rule to understand before reading any code.

### What problem does it solve, and what makes it more than CRUD?

A plain CRUD app would be "products table, orders table, list/create/update/delete." Bakeaura adds real-world constraints that turn it into a system-design exercise:

- **Geography matters**: an order can only be placed if the delivery address is within the seller's configured delivery radius (Haversine distance × a road-distance fudge factor), and ETA is computed from that distance, not guessed.
- **Money correctness matters**: stock is *validated* at order time but only *deducted* at payment-capture time (so two customers racing to buy the last cake don't both succeed), prices are *frozen* into the order at the moment of purchase (`priceAtPurchase`) so a later price change never rewrites history, and all money fields are `BigDecimal`, never `double`.
- **External systems can fail**: Cloudinary (video/image upload) and Razorpay (payments) both have Resilience4j circuit breakers with fallback methods, so the whole API doesn't grind to a halt if a third-party vendor is slow or down.
- **Real-time matters**: order status changes are pushed to the customer's browser over WebSocket/STOMP instead of forcing a refresh-button UX.
- **Abuse needs throttling**: login/register/payment endpoints are token-bucket rate-limited per IP (Bucket4j) so a script can't hammer `/api/auth/login` all day.
- **Trust needs verification**: Razorpay webhooks are HMAC-signature-verified before being trusted, payments are idempotent against duplicate webhook delivery, and reviews can only be written by a customer whose order actually reached `DELIVERED`.

That's the pitch you'd give an interviewer: *"It's a multi-role marketplace where the interesting engineering is in the constraints — geography, money, idempotency, real-time updates, and graceful degradation when a third-party API misbehaves — not in the basic CRUD."*

### Who are the four roles, concretely?

| Role | Can do |
|---|---|
| `CUSTOMER` | Browse, cart, order, pay, track, review, favourite, save addresses, apply to become SELLER/INFLUENCER |
| `SELLER` | Manage own products & storefront profile, fulfil/update orders, accept custom-cake requests, post Reels, see incoming collaboration requests from influencers |
| `INFLUENCER` | Get a referral code, post Reels, view/respond to seller collaboration requests, request wallet payouts |
| `ADMIN` | Approve/reject role applications, manage users (activate/deactivate, change role, delete), see dashboard counts, manage categories, approve/reject influencer payouts |

---

## SECTION 2 — ARCHITECTURE

### What is a "modular monolith," and why does Bakeaura use one?

A **monolith** is one deployable unit — one JVM process, one `app.jar`, one `docker run`. A **microservice architecture** is many small deployable units, each with its own database, talking over the network (HTTP/gRPC/queues). A **modular monolith** is the middle path: it's *deployed* as one process (so you get monolith simplicity — one transaction manager, one connection pool, no network calls between your own features), but *organised internally* as if it were many services, with clear package boundaries between features.

Bakeaura is a modular monolith. Proof: it's one Spring Boot app (`BakeauraBackendApplication`), one Postgres database, one `docker-compose` service called `app` — but the code under `backend/src/main/java/com/bakeaura/` is split into **28 feature packages**, each named after a business capability, not a technical layer:

```
address/  admin/  auth/  cart/  category/  cloudinary/  common/  config/
content/  customorder/  enums/  exception/  favorite/  influencer/  map/
notification/  order/  payment/  payout/  product/  reel/  referral/
review/  roleapplication/  seller/  user/  wallet/  websocket/
```

Notice it's **not** organised as `controllers/`, `services/`, `repositories/`, `entities/` (that's "layered-by-technical-role," which is the classic beginner Spring Boot tutorial structure). Instead, *each package is vertical* — `order/` contains `Order` (entity), `OrderController`, `OrderService`, `OrderRepository`, DTOs, and the `OrderCreatedEvent`, all together. This is sometimes called "package by feature" instead of "package by layer."

### The boundary rule, and why it matters

The rule actually followed in this codebase (verified by reading every service's constructor): **a service may freely inject another package's `*Service`, but should not reach into another package's `*Repository` directly.** For example:

- `CartService` needs product data to validate stock — it injects `ProductService`, **not** `ProductRepository`. Look at `CartService`'s constructor: `RedisTemplate`, `ProductService`, `UserRepository`.
- `FavoriteService` needs product data for `toDto` — it injects `ProductService`, not `ProductRepository`.
- `CategoryService` needs to check "does any product use this category before I delete it?" — it injects `ProductService.existsByCategory(id)`, not `ProductRepository`.
- `AdminService` is explicitly an *aggregator* — its whole job is to ask five other services for counts (`userRepository.count()`, `productService.countProducts()`, `orderService.countOrders()`, `paymentService.countPayments()`, `categoryService.countCategories()`), which is exactly the kind of cross-cutting read-only aggregation a boundary rule should still allow.

**One honest exception, worth naming in an interview**: almost every service (`CartService`, `FavoriteService`, `OrderService`, `PaymentService`, `SellerService`, `RoleApplicationService`, `NotificationService`, `AddressService`, and more) injects `UserRepository` directly instead of going through a `UserService.getById(...)`. `User` is treated as a *shared-kernel entity* — nearly every feature needs "the current user" or "the seller behind this order," so the codebase made a pragmatic call: `UserRepository` is essentially public infrastructure, while `ProductRepository`, `OrderRepository`, `CategoryRepository`, etc. are private to their own package. If asked in an interview "is this layering perfectly clean?", the honest answer is: *"No — `User` is a shared-kernel exception, and that's a defensible, common real-world compromise, not an accident."*

### Why this over microservices, and over a plain layered monolith?

| | Plain layered monolith | Modular monolith (Bakeaura) | Microservices |
|---|---|---|---|
| Deploy unit | 1 | 1 | N (one per service) |
| Database | 1, shared, no enforced boundaries | 1, but boundaries enforced by package + "go through the Service" convention | N, one per service, no foreign keys across services |
| Cross-feature calls | direct repository access anywhere | through the owning feature's Service | network call (HTTP/gRPC), with retries, timeouts, circuit breakers |
| Transactions | trivial (`@Transactional`, one DB) | trivial (same) | hard (sagas / eventual consistency) |
| Team scaling | gets messy fast — everyone touches everything | each feature can be owned/reasoned about independently | true team independence, but huge operational cost |
| Right choice when... | a small prototype with no growth plan | a small-to-medium team, one product, want clean seams *now*, may split into services *later* | many teams, independent scaling needs, mature DevOps |

For a solo/small-team project the size of Bakeaura, microservices would be **massive overkill** — you'd be paying the tax of distributed transactions, network failure handling, and service discovery for a problem that one Postgres database and one JVM solve trivially. The modular monolith gets you 80% of the "this is organised and won't turn into spaghetti" benefit of microservices for about 5% of the operational cost. If Bakeaura ever needed to scale a single feature independently (say, the Reel/feed system became Instagram-sized traffic), the package boundaries already drawn here are exactly the seams you'd cut along to extract a microservice — that's the real payoff of doing it this way *now*.

### Request flow at a glance

```
Browser/Postman
   │  HTTP request, Authorization: Bearer <JWT>
   ▼
Spring Boot embedded Tomcat (port 8080)
   │
   ▼
Servlet filter chain (CharacterEncodingFilter, CorsFilter, RateLimitFilter, JwtAuthFilter, ...)
   │
   ▼
DispatcherServlet → @RestController method (e.g. OrderController.createOrder)
   │
   ▼
@Service (business logic, validation, orchestration) — e.g. OrderService
   │
   ▼
@Repository (Spring Data JPA) — e.g. OrderRepository
   │
   ▼
PostgreSQL (via Hibernate, connection pooled by HikariCP)
   │
   ▼
Response flows back up, wrapped in ApiResponse<T>, serialised to JSON
```

Side-effects (sending an email, sending an SMS, broadcasting a WebSocket message, crediting a wallet) happen via either `@Async` methods or `ApplicationEvent` listeners fired from inside the main request thread — covered in detail in Section 6 and Section 8.

---

## SECTION 3 — TECH STACK

For each item: what it is (with an analogy), why Bakeaura specifically needs it, what the alternative would have been, and what breaks if you removed it.

### Spring Boot 3.5.0 (Java 21)

**What it is**: Spring Boot is a framework that takes the (very powerful, very verbose) Spring Framework and pre-wires it with sensible defaults — an embedded web server, auto-configuration based on what's on your classpath, and a single `main()` method to start everything. Analogy: plain Spring is a fully-stocked hardware store where you build the house yourself, brick by brick; Spring Boot is a prefab house kit — walls, plumbing, and wiring already roughed in, and you just finish the rooms you care about.

**Why Bakeaura uses it**: every other piece of the stack (Security, Data JPA, WebSocket, Cache, Actuator, Validation) is a Spring Boot "starter" — adding one Maven dependency auto-configures an entire subsystem. This is *the* reason a solo/small-team project can have this much functionality (auth, payments, caching, real-time, rate limiting, circuit breakers) without drowning in boilerplate.

**Alternative**: Plain Java + Servlets + manual wiring (massively more boilerplate), or a different framework entirely (Node/Express, Django, Laravel). For a Java shop, hand-rolling what Spring Boot auto-configures would be reinventing a decade of solved problems.

**What breaks without it**: nothing here would exist as a single coherent app — you'd be manually wiring a servlet container, a DI container, and configuration loading from scratch.

### Spring Security

**What it is**: a chain of filters (think airport security checkpoints, one after another) that runs on *every* request before it reaches your controller, deciding "who is this?" and "are they allowed here?" Analogy: a building with multiple checkpoints — ID check at the door, badge check at the elevator, keycard check at your floor — each checkpoint either lets you through or stops you cold.

**Why Bakeaura uses it**: it owns the entire authorization story — `SecurityConfig` declares which URLs are public vs. need a token, `@PreAuthorize("hasRole('SELLER')")` enforces fine-grained per-method rules, and it's the thing that turns "no token" into a clean `401` and "wrong role" into a clean `403` instead of a stack trace.

**Alternative**: hand-rolling your own filter that checks a header and throws exceptions. It would work for a toy project but would re-implement (worse) what Spring Security already gives you for free, and you'd lose the declarative `@PreAuthorize` style entirely.

**What breaks without it**: literally everything — there'd be no concept of "this endpoint requires SELLER," and every controller would need hand-written auth checks at the top of every method (error-prone, easy to forget on a new endpoint).

### JWT (JSON Web Tokens) via `jjwt` 0.12.3

**What it is**: a self-contained, signed "ID card" the server hands the client after login. It has three Base64 parts separated by dots — header, payload (claims), signature. Analogy: a wristband at a hotel — your room number and the hotel's stamp are printed right on it; the receptionist doesn't need to remember you, she just reads the wristband and checks the stamp is genuine. HTTP itself has no memory between requests (it's *stateless* — every request is a stranger), and the JWT is what lets the server "remember" who you are without storing anything server-side.

**Why Bakeaura uses it specifically**: `JwtUtil` (`backend/.../auth/JwtUtil.java`) generates an **access token** (15 min, `jwt.access-expiration=900000`) and a **refresh token** (7 days, `604800000`), both signed with HMAC-SHA256 using a secret from `jwt.secret`. The crucial, slightly unusual decision: **the JWT subject (`sub`) is the user's numeric database ID, not their email.** `generateAccessToken(Long userId, Role role)` calls `.subject(String.valueOf(userId))`. This matters because nearly every controller does `Long.parseLong(authentication.getName())` to get back to a primary key it can use directly in repository lookups — no extra "look up by email first" query needed on every single request.

**Alternative**: server-side sessions (a session ID cookie + a server-side session store). That doesn't scale horizontally without a shared session store (defeats the point), and doesn't fit a stateless REST API as cleanly.

**What breaks without it**: every request would need some other way to identify the caller — you'd be back to sessions, which reintroduces server memory and complicates scaling.

### Spring Data JPA + Hibernate

**What it is**: JPA is a specification ("Java classes can represent database rows"); Hibernate is the implementation that actually generates SQL. Spring Data JPA goes one step further and lets you write **just a method signature** (`List<Product> findBySellerId(Long sellerId)`) and have the SQL generated from the method name — no SQL string anywhere. Analogy: instead of writing a letter in French yourself, you describe what you want in English and a translator (Hibernate) writes the French (SQL) for you, perfectly, every time.

**Why Bakeaura uses it**: every entity (`Order`, `Product`, `User`, ...) is a plain Java class annotated `@Entity`; every repository is a one-line interface extending `JpaRepository<T, ID>`. `ProductRepository` even extends `JpaSpecificationExecutor<Product>` so `ProductService.filterProducts(...)` can build a dynamic "if keyword present, add a LIKE; if category present, add an equals" query at runtime — that's the JPA Criteria API in action.

**Alternative**: hand-written JDBC (`PreparedStatement`, manual `ResultSet` mapping) or a lighter mapper like MyBatis/jOOQ. Those give you more control over exact SQL but cost far more boilerplate for simple CRUD, which is 90% of what this app needs.

**What breaks without it**: every single repository method becomes hand-written SQL + manual object mapping. The dynamic product filter (`filterProducts`) becomes a hand-built SQL string with string concatenation — a textbook SQL-injection risk if done carelessly.

### PostgreSQL

**What it is**: an open-source relational database. Analogy: a giant, perfectly organised filing cabinet with strict rules about what can go in each drawer (column types) and cross-references between drawers (foreign keys) that the cabinet itself enforces, so you can never accidentally file an order under a customer that doesn't exist.

**Why Bakeaura uses it**: relational integrity is exactly what this domain needs — an `Order` *must* reference a real `User` (customer) and a real `User` (seller); a `Review` *must* reference a real, delivered `Order`; a `Favorite` is unique per `(user_id, product_id)` pair, enforced by a DB-level unique constraint, not just application code. `hibernate.ddl-auto: update` means Hibernate inspects your `@Entity` classes on startup and adds any missing tables/columns automatically (see Section 9 for why that's fine here but dangerous in production).

**Alternative**: a NoSQL store (MongoDB, DynamoDB) — possible, but you'd be hand-rolling referential integrity, unique constraints, and transactional multi-table writes (e.g. "save the order AND its items together" — `Order`'s `@OneToMany(cascade = CascadeType.ALL)` does this for free) that a relational DB gives you natively.

**What breaks without it**: you'd lose `@ManyToOne`/`@OneToMany` mapping entirely, foreign-key integrity, and the ability to write a single SQL `JOIN` (used in `OrderRepository.findByIdWithItems`) to fetch an order and its items in one round trip instead of N+1 queries.

### Redis

**What it is**: an in-memory key-value store, blazing fast because it (mostly) never touches disk. Analogy: a sticky-note board next to your desk versus the filing cabinet in the basement — you reach for the sticky note (Redis) for anything you need *right now and don't need forever*, and the filing cabinet (Postgres) for anything that must survive permanently.

**Why Bakeaura uses it for three completely different jobs**:
1. **Spring Cache backend** (`RedisConfig`) — `@Cacheable`/`@CacheEvict` results (categories, products, seller profiles, review summaries) are stored in Redis with a 10-minute default TTL (`spring.cache.redis.time-to-live: 600000`), so a popular `GET /api/products` doesn't re-hit Postgres on every request.
2. **The shopping cart** (`CartService`) — the cart is **not a database table at all**. It's a `CartDto` object serialized to JSON and stored under key `cart:{userId}` with a 7-day TTL. This is a deliberate design choice: carts are disposable, high-write, low-value-if-lost data — exactly what a cache is *for*, and it means cart writes never touch Postgres.
3. **Refresh token storage** (`RefreshTokenStore`) — each issued refresh token is stored under `refresh-token:{userId}` with a TTL matching the token's own expiry, so logout (`revoke`) is a single Redis `DELETE`, and a stolen-but-expired token is automatically gone from the store the moment Redis's own TTL kicks it out.

**Alternative**: store carts and cache entries in Postgres directly. It would work, but you'd be paying disk-write cost for data that's deliberately ephemeral, and you'd need your own TTL/expiry sweeping logic (Redis gives you TTL natively).

**What breaks without it**: the cart logic would need a full rewrite onto a `cart_items` SQL table; every `@Cacheable` annotation would either need a different cache provider or fall back to an in-memory (non-shared-across-instances) cache; refresh token revocation would need its own DB table and cleanup job.

### WebSocket / STOMP / SockJS

**What it is**: WebSocket is a persistent, two-way connection between browser and server (unlike normal HTTP's "ask a question, get one answer, hang up"). STOMP is a simple text messaging protocol layered on top of raw WebSocket frames (so you get "subscribe to a topic" / "send to a destination" semantics instead of raw bytes). SockJS is a fallback library that pretends to be a WebSocket even on networks/browsers that block real WebSocket connections. Analogy: a phone call (WebSocket) that stays open the whole time, instead of sending a new letter (HTTP request) every time you want an update; STOMP is the "operator" that routes your call to the right department ("topic"); SockJS is the backup landline if the wireless signal is bad.

**Why Bakeaura uses it**: `OrderTrackingService.broadcastStatusUpdate` pushes a message to `/topic/order/{orderId}` every time an order's status changes (confirmed, preparing, out for delivery, delivered, cancelled), and `NotificationService.notifyUser` pushes to `/topic/users/{userId}/notifications`. Without this, the customer's "track my order" screen would need to poll (`GET /api/orders/{id}` every few seconds) — wasteful and laggy.

**Alternative**: HTTP polling (simple but wasteful and slow) or Server-Sent Events (one-directional, would work fine here since the server only pushes, never needs the client to talk back over the same channel — WebSocket is somewhat over-engineered for Bakeaura's actual one-way usage, which is a fair observation to make in an interview).

**What breaks without it**: order tracking becomes "refresh the page to see if anything changed," and the live notification badge in the frontend navbar would need polling too.

### Razorpay

**What it is**: an Indian payment gateway — it takes care of actually moving money (UPI, cards, wallets) so Bakeaura never touches raw card numbers. Analogy: a bank teller window between the customer and the seller — Bakeaura just tells the teller "charge this much for this order" and the teller hands back a receipt; Bakeaura never holds the customer's card.

**Why Bakeaura uses it**: it's the dominant payment gateway for Indian consumers, supports UPI (critical for the Indian market this app targets), and has a Java SDK (`razorpay-java` 1.4.5) plus webhook support for asynchronous payment confirmation. `PaymentService` creates a Razorpay order the moment an internal `Order` is created (via the `OrderCreatedEvent` listener), and confirms payment two ways: (a) the frontend calls `/api/payments/verify` with the Razorpay callback's signature, and (b) Razorpay's own server calls `/api/payments/webhook` independently — both paths are HMAC-verified and both are idempotent against double-processing (see Section 6, "Idempotency").

**Alternative**: Stripe (less UPI support in India at the time), PayPal, or a "fake" payment flow for a college project. Razorpay was the right real-world choice for an India-focused marketplace.

**What breaks without it**: no real money ever changes hands; you'd need to either fake payments entirely or integrate a different gateway with a different webhook/signature scheme.

### Cloudinary

**What it is**: a cloud media (image/video) hosting and transformation service. Analogy: instead of Bakeaura's own server trying to store, resize, and serve millions of cake photos and baking videos (expensive disk + bandwidth), it ships the file to a specialist warehouse that also auto-generates thumbnails and serves everything over a fast CDN.

**Why Bakeaura uses it**: `CloudinaryService.uploadVideo`/`uploadImage` upload seller Reels and product images, and Cloudinary's `eager` transformation generates a thumbnail JPG automatically during video upload — no separate thumbnail-generation code needed on Bakeaura's side. Both calls are wrapped in `@CircuitBreaker(name = "cloudinary")` with a fallback that throws a friendly "media upload service is temporarily unavailable" error instead of hanging.

**Alternative**: store files on local disk or a raw S3 bucket and write your own resize/thumbnail pipeline (FFmpeg for video, ImageMagick for images). Far more infrastructure to own and maintain.

**What breaks without it**: `ReelService.processVideoUpload` and product image uploads would have nowhere to put files; you'd need to build (and pay to operate) your own storage + CDN + transformation pipeline.

### JavaMailSender (Gmail SMTP)

**What it is**: Spring's abstraction for sending email over SMTP. Analogy: a digital post office — you hand it an envelope (a `MimeMessage`) with a `to`, `subject`, and HTML `body`, and it deals with the actual postal mechanics of SMTP handshakes.

**Why Bakeaura uses it**: `EmailService` sends account-verification emails (with a 24-hour-expiry token link), order-confirmed and order-delivered emails, and the two-step email-change verification email (see Section 6). It's configured against Gmail SMTP (`smtp.gmail.com:587`, STARTTLS) in `application.yml`. Every send method is `@Async`, so a slow SMTP handshake never blocks the HTTP response the customer is waiting on.

**Alternative**: a transactional email API like SendGrid or AWS SES (better deliverability and analytics at scale, but requires an external account + API key instead of reusing a Gmail account — a reasonable simplification for a project this size).

**What breaks without it**: no verification emails, no order confirmation emails — registration would still "work" but accounts would never get marked email-verified through the real flow.

### Fast2SMS

**What it is**: an Indian SMS gateway — a simple HTTP GET API that sends a text message to a phone number. Analogy: like calling a courier and saying "deliver this exact message to this exact phone number," except the "courier" is an HTTP request.

**Why Bakeaura uses it specifically**: it's a low-cost, India-focused SMS provider, which fits the same "India-first marketplace" reasoning as Razorpay. `SmsService.sendOrderConfirmedSms` / `sendOutForDeliverySms` are called from `OrderService.updateStatus` when an order moves to `CONFIRMED` or `OUT_FOR_DELIVERY`, using `RestTemplate` to hit `fast2sms.com/dev/bulkV2`. Both methods gracefully no-op (with a log warning, not an exception) if the customer has no phone number on file.

**Alternative**: Twilio (the global standard, but priced for international scale and overkill/costlier for an India-only MVP) or skip SMS entirely and rely on email + push notifications.

**What breaks without it**: customers stop getting SMS updates, but the order flow itself is unaffected — SMS sending failures are already caught and logged, never thrown, by design (see `sendSms`'s try/catch).

### Spring AI / Google Gemini — **configured but not implemented**

Worth stating honestly because it's a real, verifiable gap: `application.yml` has a fully-specified Resilience4j circuit breaker named `gemini` (30s open-state wait, 2 permitted half-open calls). **There is no Spring AI dependency in `pom.xml`, no Gemini API key property, and zero Java code anywhere in the project that calls Gemini.** A `grep` across the entire `backend/src/main` for "gemini" returns exactly one match — that `application.yml` block. The most likely intent (based on `CustomOrderRequest.generatedImageUrl`, a column that exists but is never written to by any service method) is an unbuilt feature: customers describe a custom cake, and an AI image-generation call would populate a preview image. If asked about this in an interview, the honest answer is: *"It's scaffolded — there's a production-grade circuit breaker config ready to wrap a future AI call — but the integration itself was never built."* Don't claim it works; that's an easy thing for an interviewer to disprove by asking you to trace the code.

### Bucket4j (rate limiting)

**What it is**: a Java library implementing the **token bucket algorithm**. Analogy: a bucket that holds a fixed number of tokens (say, 5); every request takes one token out; the bucket *refills* back to full after a time window. If the bucket is empty, you wait — or in Bakeaura's case, you get rejected with `429 Too Many Requests`.

**Why Bakeaura uses it**: `RateLimitFilter` keeps one `Bucket` per client IP per "sensitive endpoint category" — login (5/min), register (5/min), payments (5/min) — stored in `ConcurrentHashMap`s that are thread-safe under concurrent requests. This is a simple, in-memory, single-instance defence against brute-force login attempts and payment-endpoint abuse. (There's a fourth, currently-dead bucket for `/api/v1/ai/**` — see Section 11, Known Issues.)

**Alternative**: no rate limiting at all (leaves auth endpoints open to brute force), or an external rate limiter at the API-gateway/Nginx layer (more scalable across multiple app instances, since this in-memory map resets per-instance and per-restart, but more infrastructure to set up).

**What breaks without it**: nothing functionally — the app still works — but `/api/auth/login` becomes vulnerable to unlimited password-guessing attempts from a single IP.

### Resilience4j (circuit breakers)

**What it is**: a library implementing the **circuit breaker pattern** — see Section 6 for the full CLOSED/OPEN/HALF-OPEN walkthrough. Analogy: a household electrical circuit breaker — when something downstream draws too much "current" (errors), the breaker trips and *stops* sending power (requests) to the failing component for a cooldown period, protecting the rest of the house (your app) from cascading failure.

**Why Bakeaura uses it**: wraps every call to Razorpay (`PaymentService.createRazorpayOrder`) and Cloudinary (`CloudinaryService.uploadVideo`/`uploadImage`/`deleteFile`) so that if either third-party service starts timing out or erroring, Bakeaura fails fast with a friendly message instead of every request thread hanging until a slow timeout, which is exactly the kind of failure that can take down an entire app under load (thread pool exhaustion).

**Alternative**: no protection at all (a slow Razorpay means slow/hung Bakeaura requests piling up), or hand-rolled retry/timeout logic (far more code, easy to get subtly wrong).

**What breaks without it**: a Razorpay or Cloudinary outage would directly translate into Bakeaura's own thread pool filling up with requests waiting on a dead dependency.

### Spring Cache + `@Cacheable`/`@CacheEvict`

**What it is**: a declarative caching abstraction — annotate a method, and Spring intercepts calls to check the cache before running your code, and clears the cache when data changes. Already described under Redis above; see Section 6 for the cache-invalidation walkthrough.

### Spring Actuator

**What it is**: a set of built-in operational endpoints for monitoring a running Spring Boot app. Analogy: the dashboard lights on a car — you don't need to pop the hood to know if the engine ("the app") is healthy.

**Why Bakeaura uses it**: only `/actuator/health` is exposed (`management.endpoints.web.exposure.include: health`), with `show-details: always` — useful for Docker health checks or a load balancer to know "is this container ready to receive traffic" (checks DB and Redis connectivity automatically, since Spring Boot wires health indicators for both).

**Alternative**: a hand-written `/ping` endpoint that just returns 200 (tells you the JVM is alive, but not whether Postgres/Redis are reachable).

**What breaks without it**: you'd lose automatic dependency health checking; Docker/orchestration tooling would have no reliable signal for "is this instance actually ready."

### Docker & docker-compose

**What it is**: Docker packages an app and everything it needs (JRE, dependencies) into a portable image that runs identically anywhere; docker-compose orchestrates multiple containers (app, database, cache, frontend) as one unit. Analogy: instead of mailing someone a recipe and hoping their kitchen has the right ingredients, you ship them a fully-cooked, vacuum-sealed meal — it tastes the same regardless of whose kitchen reheats it.

**Why Bakeaura uses it**: `backend/Dockerfile` is a two-stage build (build with full Maven+JDK, ship only the JRE + final jar — keeps the runtime image small); `docker-compose.yml` wires up `frontend` (Nginx serving the built React app), `app` (the Spring Boot jar), `postgres:15`, and `redis:7-alpine` with a shared bridge network, so `docker compose up --build` gives you the entire stack with zero local installs beyond Docker itself.

**Alternative**: manually installing Postgres, Redis, and a JDK on every developer's machine, and hoping versions match. Massively more error-prone ("works on my machine").

**What breaks without it**: onboarding a new developer (or deploying to a server) becomes "install seven things in the right versions by hand" instead of one command.

### Haversine formula

**What it is**: a pure-math formula for "as the crow flies" distance between two latitude/longitude points on a sphere (Earth). Analogy: if you're a bird, it's the distance you'd fly directly between two points — ignoring roads, buildings, traffic.

**Why Bakeaura uses it**: `MapService.calculateDistance` implements it directly in Java (no external API call, no network round trip, sub-millisecond, free). It's then multiplied by a configurable `road-distance-factor` (default `1.3`) in `calculateEstimatedRoadDistance` to *approximate* real road distance without actually calling a routing API — a deliberate, pragmatic trade-off: "good enough" delivery-radius and ETA math without paying for (or depending on the uptime of) Google Maps' Distance Matrix API. **Be precise about this if asked**: there is no Google Maps integration anywhere in this codebase, despite what older planning docs may say — it's Haversine math, full stop, no fallback branch because there's nothing to fall back *from*.

**Alternative**: a real routing API (Google Maps Distance Matrix, Mapbox) that accounts for actual roads, one-way streets, and traffic — more accurate, but costs money per call and adds an external dependency + circuit breaker need for something that's currently free and synchronous.

**What breaks without it**: `OrderService.createOrder`'s delivery-radius check and `updateStatus`'s ETA calculation would have no way to estimate either.

---

## SECTION 4 — DATABASE DESIGN

**A note on accuracy first**: the brief that prompted this document mentions "22 tables." Reading every `@Entity` class in the codebase, there are **20** actual JPA-backed tables. The other two pieces of "table-shaped" data you might expect — the **shopping cart** and **refresh tokens** — deliberately live in **Redis**, not Postgres (see Section 3). That's not a gap; it's a design decision: that data is disposable/session-scoped, so it doesn't need durable relational storage or its own migration history. If an interviewer asks "how many tables does this have," the precise, defensible answer is *"20 relational tables, plus two Redis-only structures (cart, refresh tokens) that were deliberately kept out of Postgres."*

### `users`

The root entity everyone else hangs off. Stores `name`, unique `email`, BCrypt `password`, `role` (enum string), `isActive`, `latitude`/`longitude` (used for delivery-radius and nearby-seller search), `phone` (used for SMS), `profileImageUrl`, `bio`, and the email-verification/email-change machinery: `isEmailVerified`, `emailVerificationToken` + expiry, and `pendingEmail` + `pendingEmailToken` + expiry (the two-step email change flow — see Section 6). **Design oddity worth knowing**: `User` itself has **zero `@OneToMany`/`@ManyToOne` JPA relationship annotations** — every other entity points *at* `User` via a foreign key (`@ManyToOne` + `@JoinColumn`), but `User` never declares the reverse side (no `@OneToMany List<Order> orders` on `User`, for example). This is a one-directional-only mapping strategy: you can always ask "which user placed this order," but you can never ask Hibernate "give me this user's orders" by navigating the object graph — you go through `OrderRepository.findByCustomer_IdOrderByCreatedAtDesc` instead. This avoids a whole category of `LazyInitializationException` and accidental-N+1 bugs that bidirectional mappings are notorious for, at the cost of needing a repository method for every "give me this user's X" query.

### `addresses`

A customer's saved delivery addresses. `@ManyToOne` to `User` (one user, many addresses), `label` (e.g. "Home," "Work"), `addressLine`, `latitude`/`longitude` (so checkout doesn't need geocoding — the address itself carries its coordinates), and `defaultAddress` (boolean). Real-world relationship: when a customer checks out, they pick a saved address instead of retyping it every time; `AddressService.clearDefaults` ensures only one address per user is ever marked default (it walks every address and force-clears the flag on all but the one being set).

### `categories`

Product taxonomy (e.g. "Cakes," "Cookies"). `name` is `unique`, `description`, `imageUrl`. Exists as its own table (rather than a free-text field on `Product`) so categories can be centrally managed by an admin, displayed as filter chips on the frontend, and — importantly — **protected from deletion while in use**: `CategoryService.deleteCategory` calls `productService.existsByCategory(id)` and throws a `BadRequestException` if any product still references it, preventing orphaned foreign keys.

### `products`

The catalogue. `name`, `description`, `price` (`BigDecimal`, never `double` — money precision), `stockQuantity`, `@ManyToOne` to `seller` (a `User` with role `SELLER`) and `category`, `imageUrl`, `isAvailable`, `isPreOrderOnly`, `minAdvanceDays` (enforces the "this cake needs 3 days notice" business rule — see Section 6). **Special design decision**: `@Version private Long version;` — optimistic locking (see Section 6) to prevent two simultaneous orders from overselling the last unit of stock.

### `orders`

One customer purchase from one seller. `@ManyToOne` to `customer` and `seller` (both `User`), `orderType` (`INSTANT`/`SCHEDULED`), `scheduledDeliveryDate`, `status` (`OrderStatus` enum), `totalAmount`, and — deliberately denormalised — `deliveryAddress`/`deliveryLatitude`/`deliveryLongitude` copied *as plain strings/doubles onto the order itself*, not as a foreign key to the `addresses` table. This is the **snapshot pattern** (Section 6): if the customer later edits or deletes that saved address, this order's historical delivery address must never change — it's a receipt, not a live pointer. Also stores `estimatedDeliveryMinutes` (computed once, at confirm-time) and `razorpayOrderId`. `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)` to `OrderItem` — saving/deleting an order cascades to its line items automatically.

### `order_items`

One product line within one order. `@ManyToOne` to `order` and `product`, `quantity`, and — critically — `priceAtPurchase`, a **second snapshot field**: the product's price *at the moment of purchase*, copied in, not looked up live from `products.price` every time you view an old order. If the seller raises the cake's price next week, every past receipt still shows what the customer actually paid. `getSubtotal()` is `@Transient` — computed on the fly (`priceAtPurchase × quantity`), never stored, because it's pure derived data with zero risk of going stale.

### `payments`

One row per order's payment attempt. `@OneToOne` to `order`, unique `razorpayOrderId`, nullable `razorpayPaymentId`/`razorpaySignature` (filled in only once payment actually happens), `status` (`PaymentStatus`), `amount`, `paidAt`. Kept as its own table (not just columns on `Order`) because payment has its own lifecycle and its own external IDs from a third party — mixing that into `Order` would conflate "did the customer pay" with "what did the customer order."

### `notifications`

In-app notification feed per user. `@ManyToOne` to `user`, `type` (a free-text tag like `"ORDER_CREATED"`, `"PAYMENT_CAPTURED"`), `message`, `relatedId` (the order/entity this notification is about — intentionally untyped/polymorphic, a pragmatic trade-off over a proper polymorphic association), `read` (boolean), with an explicit DB index on `user_id` (`idx_notification_user_id`) since "give me my notifications" is the single most common query on this table.

### `favorites`

Many-to-many join between `users` and `products`, implemented as its own entity (not a raw join table) so it can carry `createdAt`. `@UniqueConstraint(columnNames = {"user_id", "product_id"})` at the database level means "double-favouriting" the same product is impossible even if application code had a bug — the database itself is the last line of defence.

### `reviews`

A customer's rating + comment on a *seller*, tied to a specific *order*. `@ManyToOne` to `customer`, `seller`, and `order`, `rating` (1–5), `comment`. `@UniqueConstraint(columnNames = {"customer_id", "order_id"})` enforces "one review per order" at the DB level. **Integrity rule enforced in code, not schema**: `ReviewService.createReview` checks `order.getStatus() != OrderStatus.DELIVERED` and rejects the review if the order hasn't actually been delivered yet — you can't review a cake you haven't received.

### `role_applications`

A user's request to become `SELLER` or `INFLUENCER`. `@ManyToOne` to `user`, `requestedRole`, `status` (`ApplicationStatus`), `message` (applicant's pitch), `reviewNote`/`reviewedBy`/`reviewedAt` (admin's decision trail). This is the single gate through which every seller and influencer enters the platform — see Section 6 for the full approval flow and what it auto-provisions.

### `seller_profiles`

Storefront data for a `SELLER`-role user, separated from `User` because not every user has (or needs) a storefront. `@OneToOne` to `user` (unique), `shopName`, `shopBio`, `deliveryRadiusKm` (per-seller override, distinct from the platform-wide default in `application.yml`), `isOpen` (manual "open/closed" toggle the seller controls), `bannerImageUrl`, `totalRatings`/`averageRating` (note: these columns exist on `SellerProfile` but the actual review-summary math lives in `ReviewService.getSummary`, computed live from the `reviews` table via `averageRatingForSeller` — these two stored columns are currently **not** kept in sync by any code path; worth flagging as a latent inconsistency if you're asked to extend this).

### `influencer_profiles`

Storefront-equivalent for `INFLUENCER`-role users. `@OneToOne` to `user` (unique), `niche`, `instagramUrl`, `youtubeUrl`, `followerCount`, `totalReferrals`, `totalEarnings`. `totalReferrals` is incremented by `InfluencerProfileService.incrementTotalReferrals` — but see Section 11: the service that's *supposed* to call it (`ReferralOrderService.processReferral`) is never actually invoked anywhere in the order flow, so in practice this counter never moves yet.

### `influencer_collaborations`

A seller's request to collaborate with an influencer (and the influencer's response). `influencerId`/`sellerId` (plain `Long` foreign keys, not `@ManyToOne` objects — a lighter-weight style than most other entities use), `status` (`CollaborationStatus`), `message`. `@UniqueConstraint(columnNames = {"influencer_id", "seller_id"})` — one relationship per seller/influencer pair, ever (no "request again after rejection" path exists in the current code).

### `custom_order_requests`

A customer's bespoke-cake request to a specific seller. `customerId`/`sellerId` (plain `Long`s again), `designBrief`, `generatedImageUrl` (reserved for the unbuilt AI-image feature — see Section 3's Gemini note), `occasion`, `serves`, `budgetMin`/`budgetMax`, `status` (`CustomOrderStatus`), `sellerQuote`. This is a parallel, simpler order pipeline that never becomes a real `Order`/`Payment` — accepting or quoting a custom request doesn't create anything in the `orders` table; it's a negotiation thread that presumably gets finished outside the app (a known gap, not a bug — there's just no "convert accepted custom request into a real order" code path yet).

### `payout_requests`

An influencer's request to withdraw their wallet balance. `influencerId`, `amount`, `status` (`PayoutStatus`), `upiId` (where to send the money), `adminNote`, `processedBy`/`processedAt`. Notice this table never actually *moves money* — `PayoutRequestService.approveRequest` calls `walletService.debit(...)`, which only writes a row to `wallet_transactions`; there's no integration that actually sends a UPI payment. The admin's "approve" action is a bookkeeping decision, not an automated bank transfer — in a real production system, you'd wire this to a payout API and only mark `PAID` after that API confirms success (which is exactly why `PayoutStatus.PAID` exists but is never reached in code today — see Section 11).

### `referral_codes`

One unique `code` string per influencer (`@ManyToOne` to `influencer`), `isActive`. Auto-generated (not chosen by the influencer) the moment their role application is approved — `ReferralCodeService.generateUniqueCode` builds it from the influencer's name + current year + two random letters, retrying up to 10 times if a collision is found.

### `referral_orders`

One row per order that successfully used a referral code — the **append-only commission ledger**. `@ManyToOne` to `referralCode`, unique `orderId` (a single order can only ever generate one commission row — `existsByOrderId` guards this), `commissionAmount` (10% of the order total, computed once and frozen — another snapshot field, so a later change to the commission rate never rewrites historical commissions).

### `wallet_transactions`

The influencer's earnings ledger. `influencerId`, `amount`, `type` (`CREDIT`/`DEBIT`), `description`. **This is the most important design decision in the whole schema, and the one most worth being able to defend**: there is **no `balance` column anywhere**. `WalletService.getBalance` computes the balance *live*, every time, via `SUM(CASE WHEN type = CREDIT THEN amount ELSE -amount END)` (see `WalletTransactionRepository.calculateBalance`). See Section 6, "Wallet as append-only transaction log," for the full reasoning — short version: a stored `balance` column can drift from reality through any code bug, crash mid-update, or manual DB edit; a derived balance from an immutable, append-only ledger **cannot** drift, because there's nothing to drift *from* — the ledger is the single source of truth, the same principle real banks and accounting systems use.

### `reels`

Short videos sellers post to the discovery feed. `@ManyToOne` to `seller`, `caption`, `videoUrl`/`cloudinaryPublicId`/`thumbnailUrl` (filled in *after* async upload completes), `durationSeconds`, `status` (`PROCESSING` → `ACTIVE` → `FAILED`, an explicit state machine for "the upload hasn't finished yet"), and engagement counters `likeCount`/`commentCount`/`saveCount`/`viewCount` — all currently write-once-at-zero; no controller endpoint in this codebase actually increments them yet (liking/commenting/saving aren't implemented), so today they exist purely to support the feed-ranking formula's inputs (see Section 6).

---

## SECTION 5 — EVERY PACKAGE AND EVERY FILE

A quick annotation glossary you'll see repeated everywhere below, explained once:

- **`@Entity`/`@Table`** — "this Java class is a database table; each instance is a row."
- **`@Id`/`@GeneratedValue(strategy = GenerationType.IDENTITY)`** — "this field is the primary key; let Postgres auto-increment it."
- **`@ManyToOne`/`@OneToMany`/`@OneToOne`/`@JoinColumn`** — relationship mapping. `@ManyToOne` = "many of me point to one of them" (e.g. many orders → one customer); `@JoinColumn(name="...")` names the actual foreign-key column; `fetch = FetchType.LAZY` means "don't load the related row until I actually call the getter" (avoids loading the whole object graph for every query).
- **`@Version`** — optimistic-locking column (Section 6).
- **`@Service`** — "this class holds business logic; let Spring create one shared instance and inject it wherever needed."
- **`@Repository`** — "this interface talks to the database" (often omitted since `JpaRepository` already implies it).
- **`@RestController`/`@RequestMapping`** — "this class handles HTTP requests; these methods return JSON, not HTML views."
- **`@Transactional`** — "wrap this method in a database transaction; if anything throws, roll everything back" (Section 9 has the deep dive).
- **`@Cacheable`/`@CacheEvict`** — "check Redis before running this method" / "clear this cache key because the underlying data just changed."
- **`@Async`** — "run this method on a background thread pool; don't make the caller wait."
- **`@PreAuthorize("hasRole('X')")`** — "Spring Security must confirm the caller has role X before this method runs at all."
- **`@EventListener`** — "call this method whenever someone publishes a matching event," (Section 6's pub/sub pattern).
- **`@RequiredArgsConstructor`** (Lombok) — "generate a constructor that takes every `final` field," which is how Spring's constructor injection wires dependencies without you writing the constructor by hand.
- **`@Data`/`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor`/`@Builder`** (Lombok) — code-generation shortcuts for getters/setters/constructors/builder pattern, so the class itself stays focused on fields, not boilerplate.

### `BakeauraBackendApplication.java`

The entry point. `@SpringBootApplication` is itself three annotations bundled together: `@Configuration` (this class can define beans), `@EnableAutoConfiguration` (scan the classpath and wire up everything Spring Boot knows how to configure — datasource, security, web server, etc.), and `@ComponentScan` (find every `@Component`/`@Service`/`@RestController`/`@Repository` under `com.bakeaura` and register them). `main()` just calls `SpringApplication.run(...)`, which boots the embedded Tomcat server, builds the full dependency-injection container, and starts listening on port 8080. **If this file were removed**: there would be no way to start the application at all — every other file is wiring, but this is the spark.

### `address/` package

**`Address.java`** — entity for a saved delivery address. `@ManyToOne(optional = false)` to `User` (every address must belong to someone). `@CreationTimestamp`/`@UpdateTimestamp` auto-stamp `createdAt`/`updatedAt` — Hibernate fills these in for you, no manual `new Date()` anywhere. **If wrong**: a bug in `defaultAddress` handling could let a user end up with zero or multiple default addresses, confusing checkout pre-selection.

**`AddressController.java`** — `@RestController` mapped to `/api/addresses`. Five endpoints (list/create/update/setDefault/delete), every one of them pulling the caller's ID via `Long.parseLong(authentication.getName())` — this is the consistent pattern used almost everywhere in the codebase (the exception is in Section 11). **If removed**: no HTTP access to saved addresses at all; the service logic would be unreachable.

**`AddressDto.java`** — what's actually sent to the frontend: `id`, `label`, `addressLine`, `latitude`, `longitude`, `defaultAddress`. Notice it does **not** include the owning `userId` — the frontend never needs it, since every address request is already scoped to "my addresses" via the JWT. This is the **Entity vs DTO** discipline in action (Section 9).

**`AddressRepository.java`** — one custom finder: `findByUserOrderByDefaultAddressDescCreatedAtDesc`, which Spring Data derives into SQL purely from the method name — no `@Query` needed. Sorting default-address-first means the frontend's address list naturally shows the default at the top.

**`AddressRequest.java`** — the incoming-request shape, with Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@DecimalMin/@DecimalMax` for valid lat/long ranges). **If wrong**: someone could submit `latitude: 999` and it would silently corrupt downstream distance math — this is exactly why validation lives at the boundary (Section 9).

**`AddressService.java`** — the actual logic. `getOwnedAddress` is a private helper used by update/delete/setDefault that throws `AccessDeniedException` if the address doesn't belong to the calling user — this is the **object-level authorization** check that `@PreAuthorize` alone can't express (role-based checks know "are you a CUSTOMER," not "is this *specific* address yours"). `clearDefaults` walks every address for the user and force-unsets `defaultAddress` on all but one — an O(n) approach that's perfectly fine at the scale of "a few saved addresses per user."

### `admin/` package

**`AdminController.java`** — `@RequestMapping("/api/admin")` with a **class-level** `@PreAuthorize("hasRole('ADMIN')")` — every method in this controller inherits the role check; no need to repeat it five times. Exposes dashboard, list-users (optionally filtered by role), activate/deactivate, change-role, and delete-user.

**`AdminDashboardDto.java`** — five `long` counts: users, products, orders, payments, categories. Deliberately flat and simple — no nested objects, because it's a read-only summary view, not a domain entity.

**`AdminService.java`** — the aggregator described in Section 2: it injects `UserRepository` directly but every *other* count goes through that feature's own `Service` (`productService.countProducts()`, `orderService.countOrders()`, etc.) — a clean example of "ask each feature for its own number; don't reach into its table yourself." **If this file were buggy**, the admin dashboard would show wrong counts but nothing else in the app would be affected — it's read-only and side-effect-free except for `updateUserStatus`/`updateUserRole`/`deleteUser`, which do mutate the `users` table.

**`AdminUserStatusRequest.java`** / **`UpdateUserRoleRequest.java`** — tiny validated request DTOs (`@NotNull Boolean active` / `@NotNull Role role`). Splitting these into their own classes instead of reusing `UserDto` keeps the "what can an admin set" surface explicit and narrow — an admin can flip `isActive` or `role`, nothing else, and the compiler enforces that boundary.

### `auth/` package — the most important package to understand cold for an interview

**`AuthController.java`** — `/api/auth/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/verify-email-change`. All `permitAll()` in `SecurityConfig` (you can't require a token to log in — that would be a chicken-and-egg problem).

**`AuthResponse.java`** — `accessToken`, `refreshToken`, `tokenType` ("Bearer"), `email`, `role`. Deliberately **excludes** the user's numeric ID and name — the frontend has to call `GET /api/users/me` afterward if it needs those (a real, intentional minimalism in the contract, documented in the frontend's own build notes).

**`AuthService.java`** — registration creates a `User` with `role = CUSTOMER` always (you cannot register directly as SELLER/INFLUENCER/ADMIN — that's the role-application gate from Section 1), generates a random `UUID` email-verification token with a 24-hour expiry, saves the user, fires off the verification email (`@Async`, non-blocking), and *immediately* issues access + refresh tokens — **note that registering does not require email verification first**; verification is tracked (`isEmailVerified`) but not currently enforced as a login gate anywhere in `login()`. Login checks password via `passwordEncoder.matches(...)` and `isActive`, then issues tokens the same way. `refresh()` validates the incoming token is genuinely a *refresh* token (not someone replaying an access token), checks Redis has a matching stored token for that user ID (`refreshTokenStore.matches`), then **rotates** — issues a brand-new refresh token and overwrites the old one in Redis, so a refresh token can only be used once before being replaced (limits the damage if one is intercepted). `logout()` simply deletes the stored refresh token from Redis. `verifyEmail()` looks up the user by their verification token, checks it hasn't expired, and flips `isEmailVerified = true`, clearing the token so it can't be reused.

**`JwtAuthFilter.java`** — a `@Component` extending `OncePerRequestFilter` (guarantees it runs exactly once per request, even if the request gets internally forwarded). Reads the `Authorization` header, strips `"Bearer "`, and **only proceeds if**: the token is structurally/cryptographically valid (`isTokenValid`), it's specifically an *access* token (`isAccessToken` — refusing a refresh token here is what stops someone using a long-lived refresh token to make API calls directly), and the `SecurityContext` doesn't already have an authentication (defensive, in case some other filter already set one). On success, it builds a `UsernamePasswordAuthenticationToken` with the **raw numeric `userId` (a `Long`) as the principal** — not a `UserDetails` object, not the email — plus a single `ROLE_<ROLE>` authority, and stores it in `SecurityContextHolder`. This one design choice (`Long` as principal, not `UserDetails`) ripples through the entire codebase — see Section 11 for the real bug it causes in three other controllers.

**`JwtUtil.java`** — pure JWT mechanics, no Spring Security dependency, no database access (single responsibility, as its own comment says). `generateAccessToken`/`generateRefreshToken` both embed `tokenType` as a custom claim ("access" or "refresh") specifically so `isAccessToken`/`isRefreshToken` can later distinguish them — JWTs don't have a built-in "type" concept, so this is hand-rolled. `getSigningKey()` converts the configured secret string into an HMAC `SecretKey` via `Keys.hmacShaKeyFor`. **If the secret (`jwt.secret`) were ever leaked**, anyone could forge a token for any user with any role — this is the single most security-critical config value in the whole app, and `application.yml`'s default (`bakeaura_dev_secret_change_me`) is correctly *not* meant for production use.

**`LoginRequest.java`/`RegisterRequest.java`/`LogoutRequest.java`/`RefreshTokenRequest.java`** — simple validated request bodies (`@Email`, `@NotBlank`). Splitting these into one class per endpoint (rather than one shared "AuthRequest" with optional fields) means each endpoint's exact required fields are visible at a glance and enforced by the compiler + Bean Validation, not by runtime "if this field is null, ignore it" logic.

**`RefreshTokenStore.java`** — wraps Redis access for refresh tokens behind three verbs (`store`/`matches`/`revoke`), keyed `refresh-token:{userId}` with a TTL equal to the refresh token's own lifetime. **If this file were removed**, refresh tokens would have no server-side revocation mechanism at all — a logged-out user's refresh token would remain valid until it naturally expired, since JWTs are stateless by design and can't be individually invalidated without an external store like this.

### `cart/` package

**`CartController.java`** — `@RequestMapping("/api/cart")` with class-level `@PreAuthorize("hasRole('CUSTOMER')")` (only customers have carts — sellers/admins/influencers get a clean `403`, not a confusing empty cart). `@Validated` + `@Min`/`@Min(0)` on query params enforce "quantity must be ≥ 1 to add, ≥ 0 to update" at the controller boundary.

**`CartDto.java`/`CartItemDto.java`** — both `implements Serializable`, which matters because they get serialized to JSON for storage *inside Redis* (via `GenericJackson2JsonRedisSerializer`), not just for the HTTP response. `getTotalAmount()`/`getSubtotal()` are computed getters — never stored, always derived from the line items, so they can never drift out of sync with the items themselves.

**`CartService.java`** — the only service in the app whose "repository" is Redis directly (`RedisTemplate<String, Object>`), not a `JpaRepository`. `getCartForUser` always calls `syncCartWithProducts` before returning — this re-fetches every cart item's *current* product data (price, name, availability, stock) and **silently removes** items that became unavailable, out of stock, or deleted, and **clamps down** quantities that now exceed available stock. This means a cart is never trusted as "frozen" data — every read repairs itself against the live product catalogue, which is a deliberate trade-off favouring correctness (no stale prices/ghost products at checkout) over raw read speed. `handleOrderCreated` is an `@EventListener` that clears the customer's cart the moment their order is successfully created — decoupled entirely from `OrderService` (Section 6's pub/sub pattern).

### `category/` package

**`Category.java`** — straightforward entity; `name` has a DB-level `unique` constraint as a second line of defence behind the application-level `existsByNameIgnoreCase` check in the service.

**`CategoryController.java`** — public reads (`GET`), `@PreAuthorize("hasRole('ADMIN')")` per-method on writes (note: here the role check is on individual methods, not the whole class, unlike `AdminController` — because this controller mixes public and admin-only endpoints in one class).

**`CategoryRepository.java`** — `existsByNameIgnoreCase`/`existsByNameIgnoreCaseAndIdNot` (the second variant is what makes "rename category #3 to a name that's already taken by category #3 itself" *not* incorrectly rejected as a duplicate).

**`CategoryRequestDto.java`/`CategoryResponseDto.java`** — the create/update input vs. the read-out shape; identical fields today, but kept as separate classes so they can diverge later without breaking either side of the contract.

**`CategoryService.java`** — `@Cacheable(value = "categories", key = "'all'")` on `getAllCategories` and `key = "#id"` on `getCategoryById`; every write method is `@CacheEvict(value = "categories", allEntries = true)` — a deliberately blunt "wipe the whole categories cache on any write" strategy, reasonable because categories change rarely and the whole cache is small. `deleteCategory`'s product-usage check (described in Section 4) is the standout business rule here.

### `cloudinary/` package

**`CloudinaryService.java`** — three methods (`uploadVideo`, `uploadImage`, `deleteFile`), each wrapped in `@CircuitBreaker(name = "cloudinary", fallbackMethod = "...")`. The fallback method signature must exactly match the original **plus a trailing `Throwable`** — that's how Resilience4j knows which method to call when the breaker is open or the call fails (`uploadVideoFallback(MultipartFile file, String folderName, Throwable t)`). `uploadVideo`'s `eager` transformation config asks Cloudinary to *synchronously* generate a 400×711 JPG thumbnail at upload time (`eager_async: false`) — that thumbnail URL comes back in the same response, no second round trip needed.

### `common/` package

**`ApiResponse.java`** — the **single response envelope every controller in this app uses**. `success`/`message`/`data`/`errorCode`/`timestamp`, with `@JsonInclude(JsonInclude.Include.NON_NULL)` so successful responses don't show `null` `errorCode`/`timestamp` fields in the JSON (keeps successful payloads clean while still allowing those fields when there's an error). Two static factories, `ApiResponse.ok(msg, data)` and `ApiResponse.error(msg, code)`, are used by literally every controller method in the project and by `GlobalExceptionHandler`. **If this file were removed**, nothing would compile — it's the most universally depended-on class in the codebase.

### `config/` package — the wiring room

**`AsyncConfig.java`** — `@EnableAsync` turns on Spring's `@Async` processing for the whole app (without this, every `@Async` annotation elsewhere would be silently ignored and methods would run synchronously). Also defines a `RestTemplate` bean (used by `SmsService` to call Fast2SMS).

**`CloudinaryConfig.java`** — builds the `Cloudinary` client bean from three `@Value`-injected properties (cloud name, API key, secret) plus `secure: true` (force HTTPS URLs).

**`CorsConfig.java`** — implements `WebMvcConfigurer` *and* defines a `CorsConfigurationSource` bean — belt-and-suspenders, because Spring MVC's CORS handling and Spring Security's CORS handling are two separate subsystems that both need to agree, and `SecurityConfig`'s `.cors(Customizer.withDefaults())` specifically picks up the `CorsConfigurationSource` bean. Allowed origins come from `app.cors.allowed-origins` (a comma-separated env var, defaulting to `localhost:3000`/`5173`).

**`JwtConfig.java`** — an **empty class with no fields, no methods, no annotations**. Genuinely dead code — a stub that was probably meant to hold `@ConfigurationProperties`-style JWT settings but was superseded by the simple `@Value` injections directly inside `JwtUtil`. Harmless, but worth deleting if you're doing cleanup, and a good "spot the dead code" talking point.

**`RateLimitFilter.java`** — see Section 3 for the token-bucket mechanics. Note this is a plain `@Component extends OncePerRequestFilter`, **not** wired into `SecurityConfig`'s filter chain via `addFilterBefore` — Spring Boot auto-registers any `Filter` bean into the servlet container's generic filter chain (separate from, and running alongside, Spring Security's own internal `FilterChainProxy`). That's why it doesn't need explicit registration the way `JwtAuthFilter` does.

**`RazorpayConfig.java`** — one `@Bean` building a `RazorpayClient` from the configured key ID/secret. If these are wrong (e.g. still the placeholder `rzp_test_your_key`), the entire payment flow fails at the Razorpay-order-creation step, not at the verify step — the SDK will throw on the very first real API call.

**`RedisConfig.java`** — `@EnableCaching` (without this, every `@Cacheable`/`@CacheEvict` annotation in the app would be a no-op). Defines two beans: a `RedisCacheManager` (backs `@Cacheable`, 10-minute default TTL, JSON serialization via a Jackson `ObjectMapper` configured with `JavaTimeModule` so `LocalDateTime` fields serialize correctly instead of throwing), and a `RedisTemplate<String, Object>` (the lower-level handle used directly by `CartService` and `RefreshTokenStore` for manual Redis operations that aren't simple method-level caching).

**`SecurityConfig.java`** — the single most-read file if you want to understand "what's public vs. protected" in one glance. `@EnableWebSecurity` + `@EnableMethodSecurity` (the second one is what makes `@PreAuthorize` annotations elsewhere actually get enforced). CSRF is disabled (`csrf.disable()`) — correct for a stateless, token-based REST API serving a SPA, since CSRF protection exists specifically to defend cookie-based session auth, which this app doesn't use. `sessionCreationPolicy(STATELESS)` tells Spring Security "never create or use an `HttpSession`" — reinforcing that JWT, not a session cookie, is the only identity mechanism. The `authorizeHttpRequests` block whitelists exactly what's public: all `OPTIONS` requests (CORS preflight), `/`, `/error`, every `/api/auth/**` path, the payment webhook and config endpoints, all of `/ws/**` (websocket handshake — see Section 11 for the security implication), and `GET` on products/categories/sellers/influencers/content. Everything else falls through to `.anyRequest().authenticated()`. `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` is what physically inserts the custom JWT filter into Spring Security's chain, positioned to run *before* Spring's own default form-login filter (which this app never actually uses, since login is a custom `/api/auth/login` REST endpoint, not Spring's built-in form login). Also defines the `PasswordEncoder` (`BCryptPasswordEncoder`) and exposes an `AuthenticationManager` bean (present for completeness/future use — `AuthService` currently does its own manual `passwordEncoder.matches()` check rather than delegating to this `AuthenticationManager`).

**`WebSocketConfig.java`** — `@EnableWebSocketMessageBroker` turns on STOMP-over-WebSocket support. `enableSimpleBroker("/topic")` uses Spring's built-in **in-memory** message broker (fine for a single-instance deployment; a multi-instance deployment would need an external broker like RabbitMQ via `enableStompBrokerRelay` instead, since one instance's in-memory broker can't see messages destined for a client connected to a different instance). `/app` is the prefix for client-to-server messages routed to `@MessageMapping` methods; `/ws` is the actual handshake endpoint, with SockJS fallback enabled.

### `content/` package

**`ContentController.java`** — one endpoint, `GET /api/content/feed`, taking **zero parameters** — despite the frontend's `contentApi.feed(params)` being written to accept `type`/`q`/`sellerId`/`page`/`size` (see Section 11 — those params are silently ignored server-side today).

**`ContentService.java`** — `getRankedFeed()` is the feed-ranking algorithm (full math in Section 6). Pulls every `ACTIVE` reel, looks up each seller's average rating via `SellerProfileRepository`, computes a weighted score per reel, and sorts descending. `@Transactional(readOnly = true)` is a performance hint to Hibernate (no dirty-checking overhead needed since nothing here is mutated) and to the database driver (some setups can route read-only transactions to a replica, though this app's single-Postgres setup doesn't exploit that yet).

**`FeedItem.java`** — the flattened, frontend-facing shape of a ranked reel: video/thumbnail URLs, caption, seller name/ID, the four engagement counters, the seller's average rating, and the computed `score` itself (exposed so the frontend *could* show "why is this ranked here," though nothing currently does).

### `customorder/` package

**`CustomOrderRequest.java`** — entity for a bespoke-cake negotiation thread (full field list in Section 4). `@PrePersist` is a JPA lifecycle hook — Hibernate calls `onCreate()` automatically right before the `INSERT`, which is how `createdAt` and the initial `PENDING` status get set without the service needing to remember to do it on every creation path.

**`CustomOrderRequestController.java`** — customer submits/lists their own requests; seller lists all/pending requests and can accept/reject/quote. Every method manually does `Long.parseLong(authentication.getName())`.

**`CustomOrderRequestRepository.java`** — `existsByCustomerIdAndSellerIdAndStatus` backs the "you already have a pending request with this seller" guard.

**`CustomOrderRequestService.java`** — `findAndValidateSeller` is the shared object-level-authorization helper used by accept/reject/sendQuote, throwing `IllegalStateException` if the calling seller doesn't own the request. **Note**: this service uses plain `IllegalStateException`/`IllegalArgumentException` rather than the app's own `BadRequestException`/`ResourceNotFoundException`/`AccessDeniedException` — meaning `GlobalExceptionHandler` doesn't have a specific handler for these, so they fall through to the generic `Exception.class` handler and come back as an opaque `500 Something went wrong` instead of a clean `400`/`403`/`404`. Worth fixing if you ever touch this package (and a good "spot the inconsistency" interview answer).

### `enums/` package

Nine plain Java `enum`s, each a closed set of valid values stored as their *name string* in the database (`@Enumerated(EnumType.STRING)` wherever they're used on an entity — never `EnumType.ORDINAL`, which would silently break if someone ever reordered the enum's declared values). `ApplicationStatus` (PENDING/APPROVED/REJECTED — role applications), `CollaborationStatus` (same three values — influencer/seller collabs), `CustomOrderStatus` (PENDING/ACCEPTED/REJECTED/QUOTED), `OrderStatus` (PENDING→CONFIRMED→PREPARING→OUT_FOR_DELIVERY→DELIVERED, with CANCELLED as an off-ramp — the state machine `OrderService.validateTransition` enforces), `OrderType` (INSTANT/SCHEDULED), `PaymentStatus` (PENDING/CAPTURED/FAILED/REFUNDED — `REFUNDED` is declared but no code path ever sets it, see Section 11), `PayoutStatus` (PENDING/APPROVED/PAID/REJECTED — `PAID` is likewise declared-but-unreachable), `Role` (CUSTOMER/SELLER/ADMIN/INFLUENCER), `WalletTransactionType` (CREDIT/DEBIT). **If any of these files were edited carelessly** (e.g. removing a value still referenced elsewhere), the app would fail to *compile*, not just misbehave at runtime — that's actually a feature of using enums over raw strings: the compiler catches typos and removed values immediately.

### `exception/` package

**`BadRequestException.java`/`ResourceNotFoundException.java`** — two tiny `RuntimeException` subclasses, each just a constructor taking a message. Their entire value is in *being a distinct type* that `GlobalExceptionHandler` can catch and map to the right HTTP status — `400` and `404` respectively.

**`GlobalExceptionHandler.java`** — `@RestControllerAdvice` makes this a single, app-wide net that catches exceptions thrown by *any* controller method, so individual controllers never need their own try/catch blocks. Each `@ExceptionHandler` maps one exception type to one HTTP status + `errorCode`: `BadRequestException`→400/`BAD_REQUEST`, `ResourceNotFoundException`→404/`RESOURCE_NOT_FOUND`, `MethodArgumentNotValidException`→400/`VALIDATION_ERROR` (thrown automatically by Spring when `@Valid` fails on a request body — the handler stitches every failed field into one readable message), `ConstraintViolationException`→400/`VALIDATION_ERROR` (the equivalent for `@Validated` *method parameters*, e.g. `CartController`'s `@Min` on query params), `MissingServletRequestParameterException`→400, `DataIntegrityViolationException`→400 (a DB constraint — like a unique index — was violated; logged as a warning since it usually means the application-level check that *should* have caught this earlier had a gap), `AccessDeniedException`→403/`ACCESS_DENIED`, and a catch-all `Exception`→500 that **logs the full stack trace but never leaks it to the client** — the client only ever sees `"Something went wrong"`, which is the correct security posture (don't expose internals in error messages). **If this file were removed**, every unhandled exception would produce Spring Boot's default raw error page/JSON instead of the app's consistent `ApiResponse` envelope, breaking the frontend's uniform error-handling code (`error?.response?.data?.message`).

### `favorite/` package

**`Favorite.java`** — join entity, `@UniqueConstraint` on `(user_id, product_id)` as described in Section 4.

**`FavoriteController.java`** — list/add/remove/check, all under `/api/favorites` (American spelling — the frontend's route is `/favourites` with a "u," but every API call goes to `/favorites` without one; a real, easy-to-trip-on spelling mismatch the frontend code deliberately documents to itself).

**`FavoriteRepository.java`** — `existsByUserAndProduct`/`findByUserAndProduct` back the idempotent add/remove logic.

**`FavoriteService.java`** — `addFavorite` checks `existsByUserAndProduct` before inserting (so favouriting an already-favourited product is a harmless no-op, not a duplicate-row error or an exception) and every method returns the *full updated list*, not just the single item — a deliberate API design choice that saves the frontend a second round-trip to refresh the list after every add/remove.

### `influencer/` package

**`CollaborationRequest.java`** — single-field request body (`message`), used (optionally — `@RequestBody(required = false)`) when a seller proposes a collaboration.

**`CollaborationResponse.java`** — flattened response DTO for a collaboration row.

**`InfluencerCollaboration.java`** — entity (Section 4). Uses plain `Long influencerId`/`sellerId` columns rather than `@ManyToOne User` — a lighter-weight style than most of the codebase, presumably because this entity never needs to navigate to the full `User` object, only compare IDs.

**`InfluencerCollaborationController.java`** — `/api/collaborations`, four endpoints (request/incoming/outgoing/respond). **Uses `@AuthenticationPrincipal UserDetails userDetails`** — flagged in Section 11 as one of three places this breaks at runtime, because nothing in this codebase ever sets a `UserDetails` as the security principal.

**`InfluencerCollaborationRepository.java`** — `existsByInfluencerIdAndSellerId` backs the "one relationship per pair, ever" rule.

**`InfluencerCollaborationService.java`** — `requestCollaboration` double-checks both parties' actual roles server-side (`seller.getRole().equals(Role.SELLER)`, `influencer.getRole().equals(Role.INFLUENCER)`) rather than trusting the `@PreAuthorize` role check alone — defence in depth, since `@PreAuthorize` only confirms *the caller's* role, not the role of the *other* user ID passed in the URL.

**`InfluencerProfile.java`** — entity (Section 4); `@PrePersist` defaults `totalReferrals`/`totalEarnings` to zero so they're never `null`.

**`InfluencerProfileController.java`** — `/api/influencer/profile` (get/update own; admin can fetch any by `userId`).

**`InfluencerProfileRepository.java`** — `findByUserId`/`existsByUserId`.

**`InfluencerProfileResponse.java`** — flattened, includes the user's `name`/`email` alongside the profile fields, so the frontend doesn't need a second call to `/users/me` just to show whose profile this is.

**`InfluencerProfileService.java`** — `createProfileForNewInfluencer` is called exactly once, from `RoleApplicationService.approve`, the moment an INFLUENCER application is approved — it's idempotent (`existsByUserId` guard) in case it were ever accidentally called twice. `incrementTotalReferrals` exists but, as noted in Section 4, its only intended caller (`ReferralOrderService.processReferral`) is itself dead code today (Section 11).

**`InfluencerProfileUpdateRequest.java`** — partial-update DTO; every field nullable, and the service only overwrites a field if the incoming value is non-null (`if (request.getNiche() != null) ...`) — a simple, explicit alternative to a PATCH-merge library.

### `map/` package

**`MapService.java`** — the only service in this app with **zero database or network dependency** — pure math plus four `@Value`-injected config numbers (max delivery radius, base prep time, average speed, road-distance factor). `@PostConstruct void validateConfiguration()` runs once at startup and throws `IllegalStateException` immediately if any config value is nonsensical (e.g. zero speed) — **fail fast at boot**, not three weeks later when the first delivery-ETA calculation silently divides by zero in production. `calculateDistance` is the raw Haversine formula (Section 3); `calculateEstimatedRoadDistance` multiplies by the fudge factor; `isWithinDeliveryRadius` is the literal one-line gate `OrderService` calls before allowing an order; `getEstimatedDeliveryMinutes` combines travel time (`distance / speed × 60`) with a fixed base prep time, rounded up (`Math.ceil`) so estimates never *under*-promise.

### `notification/` package

**`EmailService.java`** — four `@Async` send methods plus one shared private `sendEmail` helper using `MimeMessageHelper` for HTML bodies. Every public method's exceptions are caught *inside* `sendEmail` and only logged — an email failure never bubbles up and never fails the HTTP request that triggered it (registering, confirming an order, etc. all succeed even if Gmail SMTP is briefly down).

**`Notification.java`** — entity (Section 4); explicit `@Index` on `user_id`.

**`NotificationController.java`** — paginated list (`page`/`size` query params, defaulting 0/20), unread count, mark-one-read, mark-all-read.

**`NotificationDto.java`** — flattened read shape.

**`NotificationRepository.java`** — both a `List<Notification>` and a `Page<Notification>` overload of the same "find by user, newest first" query — the `List` version is used internally by `markAllRead` (it needs every row, not a page), the `Page` version backs the actual paginated list endpoint.

**`NotificationService.java`** — `notifyUser` is the one method every other feature calls to create a notification; it **does two things in one call**: saves the row to Postgres *and* pushes it live over WebSocket (`messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", dto)`) — so a connected client sees it instantly, and a disconnected client still sees it next time they call `GET /api/notifications`. `handleOrderCreated` is an `@EventListener` that notifies the *seller* the moment an order comes in — decoupled from `OrderService` entirely (Section 6).

**`SmsService.java`** — mirrors `EmailService`'s "never let a notification failure break the main flow" philosophy; both send methods check for a missing phone number *before* attempting to send, logging a warning and returning early rather than calling an API with an empty number.

### `order/` package — the most business-logic-dense package in the app

**`CreateOrderFromCartRequestDto.java`/`CreateOrderRequestDto.java`** — two ways to create an order: with explicit `items` (a list of `productId`+`quantity` pairs, each validated `@Min(1)`), or implicitly from the customer's current cart. Both carry `sellerId`, delivery address + coordinates (validated lat/long ranges), `orderType`, optional `scheduledDeliveryDate`, and an optional `referralCode`.

**`Order.java`** — the central entity (full field breakdown in Section 4). `@Builder` (Lombok) is used here (and on several other entities) so `OrderService` can construct one fluently (`Order.builder().customer(...).seller(...)...build()`) instead of a long chain of setters. `addItem(OrderItem item)` is the one hand-written method on the entity — it does double duty, adding to the `items` list *and* setting the item's back-reference (`item.setOrder(this)`), which is required for the `@OneToMany(mappedBy = "order")` relationship to actually persist correctly (Hibernate needs the *owning* side, `OrderItem.order`, set — just adding to the `List` on the `Order` side alone wouldn't be enough).

**`OrderController.java`** — seven endpoints: create, create-from-cart, update-status (seller/admin), my-orders (customer, paginated), seller-orders (seller, paginated, optional status filter), get-by-id (any authenticated user, with object-level checks inside the service), cancel (customer only).

**`OrderCreatedEvent.java`** — extends Spring's `ApplicationEvent`; carries the saved `Order`, the customer's email, and the (possibly null) referral code. This is the **publish side** of the pub/sub pattern detailed in Section 6 — three completely separate services (`PaymentService`, `CartService`, `NotificationService`, `OrderTrackingService`) all react to this one event without `OrderService` knowing or caring that they exist.

**`OrderItem.java`** — entity (Section 4); `getSubtotal()` is `@Transient` (computed, never a real column).

**`OrderItemRepository.java`** — a bare `JpaRepository<OrderItem, Long>` with zero custom methods — it exists mainly so Spring Data JPA infrastructure recognises `OrderItem` as independently queryable, even though in practice every access happens through `Order.getItems()`.

**`OrderRepository.java`** — `findByIdWithItems` uses an explicit `@Query` with `JOIN FETCH o.items i JOIN FETCH i.product` — this is the **N+1 prevention** pattern (Section 9): one SQL query that eagerly loads the order, its items, *and* each item's product in a single round trip, instead of the default lazy-loading behaviour that would issue one query for the order, then one more per item to fetch its product.

**`OrderResponseDto.java`** — the customer/seller-facing shape, with a nested static `OrderItemResponse` class — deliberately never exposes the raw `Order`/`OrderItem` entities (Section 9's Entity-vs-DTO discipline) so internal fields (`customer`/`seller` as full `User` objects, Hibernate proxy artifacts) never leak into the API.

**`OrderService.java`** — the heart of the order flow. `createOrder` runs, in order: load customer & seller, confirm the target really is a `SELLER`, confirm both seller and delivery coordinates exist, compute road distance and reject if outside the seller's radius, validate the pre-order advance-notice rule for `SCHEDULED` orders (Section 6), build the `Order` and its `OrderItem`s (re-validating each product belongs to *this* seller, is available, and has enough stock), sum the total, save, **publish `OrderCreatedEvent`**, then save again (because the event listeners — specifically `PaymentService`'s — mutate the order by attaching a `razorpayOrderId`, and that mutation needs a second flush to persist; this double-save is a subtle but real consequence of doing synchronous, same-thread event publishing mid-transaction). `updateStatus` is the order state machine's enforcement point: `validateTransition` is a Java 21 `switch` expression encoding exactly which status can move to which next status, and `CONFIRMED`/`DELIVERED`/`OUT_FOR_DELIVERY` transitions each trigger their own side effects (ETA calculation + email + SMS; delivery email; SMS, respectively). `cancelOrder` only allows cancelling from `PENDING` or `CONFIRMED` — once a cake is `PREPARING`, the customer can no longer back out unilaterally. **If `validateTransition` had a bug** (e.g. allowing `PENDING → DELIVERED` directly), a seller could mark an order delivered without ever confirming or preparing it — a real operational/trust problem, not just a cosmetic one.

### `payment/` package

**`Payment.java`** — entity (Section 4); `@OneToOne` to `Order`, unique `razorpayOrderId`.

**`PaymentController.java`** — webhook (public, signature-protected), get-config (public — the frontend needs the Razorpay key ID and currency *before* a user logs in, to render the checkout button), verify (customer), get-by-order-id (any authenticated user, with object-level checks).

**`PaymentRepository.java`** — `findByRazorpayOrderId`/`findByOrder_Id`.

**`PaymentResponseDto.java`** — flattened read shape, used by both the verify endpoint and the get-by-order endpoint.

**`PaymentService.java`** — the most security-sensitive file in the project. `handleOrderCreated` (an `@EventListener`, **not** an HTTP-triggered method — proof that creating a Razorpay order is a *side effect of* creating an internal order, not a separate manual step) calls `createRazorpayOrder` (wrapped in `@CircuitBreaker(name = "razorpay")`) and immediately creates a `PENDING` `Payment` row. `verifyPayment` (called from the frontend after the Razorpay checkout popup succeeds) and `handleWebhook` (called by Razorpay's own servers, completely independent of the frontend) are **two separate paths that can both try to mark the same payment captured** — this is exactly why `verifyPayment` and `handlePaymentCaptured` both check `if (payment.getStatus() != PaymentStatus.CAPTURED)` before doing anything, making the "mark captured + reduce stock + confirm order" sequence **idempotent**: whichever path arrives first wins, and the second arrival is a safe no-op (Section 6, "Idempotency"). `verifySignature`/`verifyWebhookSignature` both implement the exact same HMAC-SHA256-then-hex-then-constant-time-compare pattern (`MessageDigest.isEqual`, which resists timing attacks — a naive `String.equals` comparison of secrets can leak timing information character-by-character). `reduceStock` is where stock is *actually* decremented (note: **not** at order-creation time — only at payment-capture time), and it re-checks `item.getQuantity() > product.getStockQuantity()` even at this late stage, throwing if insufficient — the last possible safety net against overselling. `toPaise` converts rupees to the smallest currency unit Razorpay expects (paise — ₹1 = 100 paise), using `RoundingMode.HALF_UP` and `intValueExact()` (which throws if the result somehow doesn't fit an `int` — a deliberate "fail loudly rather than silently truncate money" choice).

**`RazorpayConfigResponse.java`** — just `keyId` + `currency` — explicitly **never** includes the secret key (only the public key ID is safe to send to a browser).

**`VerifyPaymentRequest.java`** — the three fields Razorpay's client-side checkout returns after a successful payment (`razorpayOrderId`, `razorpayPaymentId`, `razorpaySignature`), all `@NotBlank`.

### `payout/` package

**`PayoutRequest.java`** — entity (Section 4); `@PrePersist` defaults status to `PENDING`.

**`PayoutRequestController.java`** — influencer submits/views history; admin views pending and approves/rejects.

**`PayoutRequestRepository.java`** — `existsByInfluencerIdAndStatus` backs "you already have a pending payout request."

**`PayoutRequestService.java`** — `submitRequest` checks the influencer's *live, derived* wallet balance (`walletService.getBalance`) covers the requested amount before allowing the request to even be created — note this means the balance check happens twice in the full lifecycle (once optimistically at submit time, once for real via `walletService.debit`'s own internal check at approve time), which is intentional: it gives the influencer immediate feedback ("insufficient balance") without waiting for an admin, while the actual debit still re-validates at the moment money is "spent," in case the balance changed in between (e.g. another payout was approved first).

### `product/` package

**`Product.java`** — entity (Section 4). `@Version private Long version;` is the optimistic-locking field (Section 6) — Hibernate appends `AND version = ?` to every `UPDATE` and bumps it by one on success; if two threads both loaded version 5 and both try to save, the second one's `UPDATE ... WHERE id=? AND version=5` matches zero rows (because the first save already bumped it to 6), and Hibernate throws `OptimisticLockException` instead of silently overwriting.

**`ProductController.java`** — public list/search/filter/by-category/by-seller/get-by-id; `@PreAuthorize("hasRole('SELLER')")` on create/update/delete. **Consistently uses `Long.parseLong(auth.getName())`** — confirming (after checking) that the once-suspected "ProductController uses email as principal" inconsistency does **not** exist in the current code; it's aligned with the rest of the app.

**`ProductCreateDto.java`** — validated create/update input (`@DecimalMin("0.0")` on price, `@Min(0)` on stock — you cannot create a product with negative price or negative stock; the database wouldn't stop you, but Bean Validation does, at the boundary).

**`ProductDto.java`** — the public read shape; flattens `seller`/`category` down to just their `id`+`name`, never exposing the full nested `User`/`Category` entities. `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")` pins the exact date-time string format sent to the frontend, independent of whatever Jackson's global default happens to be.

**`ProductRepository.java`** — extends **both** `JpaRepository` and `JpaSpecificationExecutor<Product>` — the second interface is what unlocks `filterProducts`'s dynamic, runtime-built `Specification` query (the JPA Criteria API). Also has several derived-query finders (`findBySellerId`, `findByCategoryId`, `existsByCategoryId`, `findByIsAvailableTrue`, `findByNameContainingIgnoreCase`).

**`ProductService.java`** — `createProduct`/`updateProduct`/`deleteProduct` all double-check the calling user is genuinely a `SELLER` and (for update/delete) genuinely *owns* this specific product — the same object-level-authorization pattern seen throughout the app. `filterProducts` builds a dynamic `Predicate` list — keyword (case-insensitive `LIKE`), category, seller, min/max price, and an `isAvailable` filter that **defaults to `true` when not explicitly specified** (`available == null || available` — meaning the public-facing filter endpoint never shows unavailable products unless you explicitly ask for them). Every read method is `@Cacheable`, every write method is `@CacheEvict(allEntries = true)` on the same `"products"` cache region — same blunt-but-simple invalidation strategy as `CategoryService`.

### `reel/` package

**`Reel.java`** — entity (Section 4); a nested `enum ReelStatus { PROCESSING, ACTIVE, FAILED }` declared right inside the entity class (since it's only ever meaningful in the context of a `Reel`).

**`ReelController.java`** — `POST /api/reels/upload` (multipart form, video + caption, SELLER or INFLUENCER), `GET /api/reels/feed` (public, paginated, **chronological** — not the ranked-by-score feed; that's `ContentController`'s job, a separate, distinct endpoint that happens to read from the same `reels` table), `GET /api/reels/seller/{id}` (a seller's own reels). Validates content-type starts with `"video/"` and size ≤ 200MB *before* even reaching the service. **Uses `@AuthenticationPrincipal UserDetails userDetails`** — the second of three places this breaks at runtime (Section 11).

**`ReelRepository.java`** — note the slightly unusual `@Query("... WHERE r.status = com.bakeaura.reel.Reel.ReelStatus.ACTIVE")` on `findBySeller_StatusActive` — a fully-qualified enum literal inside JPQL, used instead of a derived `findByStatus(ReelStatus)` method purely as a style choice (both would work identically).

**`ReelResponseDTO.java`** — flattened read shape; `status` is sent as a plain `String` (`reel.getStatus().name()`), not the enum type itself.

**`ReelService.java`** — a genuinely well-designed two-phase upload: `initiateUpload` runs **synchronously** on the request thread, creates a `PROCESSING` row, and returns immediately (so the HTTP response comes back fast, before any slow video processing happens); `processVideoUpload` is `@Async` and does the actual (slow) Cloudinary upload, then either flips the row to `ACTIVE` (with the returned `secure_url`/`public_id`/`duration`/thumbnail) or `FAILED`, and **pushes the result over WebSocket** to `/topic/reels/{sellerId}` either way — this is exactly the pattern the frontend's `ReelUploadPage` is built around (upload → show "processing" spinner → WebSocket message flips it to "done" or "error" with zero polling). **If `processVideoUpload` had a bug** that never updated the row, every reel would be stuck `PROCESSING` forever, invisible to the public feed (which only ever queries `ACTIVE` reels).

### `referral/` package

**`ReferralCode.java`** — entity (Section 4); `@PrePersist` defaults `isActive` to `true`.

**`ReferralCodeRepository.java`** — `findByCode`/`existsByCode`/`findByInfluencerIdAndIsActiveTrue`.

**`ReferralCodeResponse.java`** — flattened read shape.

**`ReferralCodeService.java`** — `generateAndSaveReferralCode` is called exactly once per influencer, from `RoleApplicationService.approve`. `generateUniqueCode` builds a code from the influencer's name (alpha characters only, uppercased, capped at 6 chars) + the current year + two random letters, retrying the random suffix up to 10 times against `existsByCode` before giving up with a `RuntimeException` — a pragmatic collision-avoidance loop rather than a cryptographically-guaranteed-unique scheme (entirely appropriate at this scale: a handful of influencers, not millions).

**`ReferralOrder.java`** — entity (Section 4); the append-only commission ledger row.

**`ReferralOrderRepository.java`** — `existsByOrderId` (the idempotency guard), `findByReferralCode_Id`.

**`ReferralOrderService.java`** — `processReferral` is fully implemented and, on paper, does exactly the right thing: guard against double-processing, look up and validate the code, compute a frozen 10% commission, save the ledger row, increment the influencer's referral counter, and credit their wallet. **The catch, verified by searching the entire codebase: nothing ever calls this method.** `OrderCreatedEvent` carries a `referralCode` field all the way from `CreateOrderRequestDto` through `OrderService.createOrder`, but no `@EventListener` anywhere subscribes to that event and forwards the code into `ReferralOrderService.processReferral`. This is a real, complete, dead feature — see Section 11.

### `review/` package

**`Review.java`** — entity (Section 4); `@UniqueConstraint(columnNames = {"customer_id", "order_id"})`.

**`ReviewController.java`** — note the URL design: `GET /api/sellers/{sellerId}/reviews` and `POST/DELETE /api/orders/{orderId}/reviews` — reviews are nested under *both* sellers (for reading — "show me this seller's reviews") and orders (for writing — "review the order you actually received") depending on which makes sense for that operation, rather than having their own top-level `/api/reviews` resource.

**`ReviewDto.java`** — flattened; includes `customerName` so the frontend can render "Jane D. rated this 5 stars" without a second lookup.

**`ReviewRepository.java`** — `averageRatingForSeller` uses `@Query` with `coalesce(avg(r.rating), 0)` — the `coalesce` is essential: `AVG()` over zero rows returns SQL `NULL`, not `0`, and without `coalesce` a brand-new seller with no reviews would crash this query's mapping into a `Double` (or silently return `null` and NPE downstream) instead of cleanly showing a `0.0` average.

**`ReviewSummaryDto.java`** — `sellerId`/`averageRating`/`reviewCount` — note the field name `reviewCount`, not `totalCount` (an exact wording the frontend's own integration notes specifically call out as easy to get wrong).

**`ReviewService.java`** — `createReview`'s three guards, in order, are the whole business rule: you must own the order, the order must be `DELIVERED`, and you must not have already reviewed it. `@CacheEvict(value = "reviewSummaries", key = "#result.sellerId")` on `createReview` is a neat Spring Expression Language (SpEL) trick — `#result` refers to the *return value* of the method (the just-created `ReviewDto`), so the cache key for eviction is computed *from the method's own output*, not from one of its input parameters. `deleteReview`'s eviction (`key = "#result"`) works the same way, since that method returns the `sellerId` directly.

### `roleapplication/` package — the gateway between roles

**`AdminRoleApplicationController.java`** — `/api/admin/role-applications`, class-level `@PreAuthorize("hasRole('ADMIN')")`; list/filter, approve, reject.

**`RoleApplication.java`** — entity (Section 4).

**`RoleApplicationController.java`** — `/api/role-applications`; any authenticated user can apply or list their own past applications.

**`RoleApplicationRepository.java`** — `existsByUserAndRequestedRoleAndStatus` (the "already pending" guard), plus filtered/unfiltered list queries for the admin view.

**`RoleApplicationRequest.java`/`RoleApplicationResponse.java`/`RoleApplicationReviewRequest.java`** — request/response/review-decision DTOs; `RoleApplicationRequest` restricts `requestedRole` to a free `Role` value at the DTO level, with the actual SELLER/INFLUENCER-only restriction enforced in the service (a deliberate split: the DTO accepts any `Role` enum value structurally, the service decides which values are *meaningful* here).

**`RoleApplicationService.java`** — the **single most important orchestration point in the whole app**. `apply` blocks applying for a role you already have, and blocks a second pending application for the same role. `approve` is where the magic happens: it flips the user's actual `role` column, then — **only in this one place in the entire codebase** — conditionally calls `sellerService.createProfileForNewSeller(user)` or both `influencerProfileService.createProfileForNewInfluencer(user)` *and* `referralCodeService.generateAndSaveReferralCode(user)`, depending on which role was requested. This is the moment a `CUSTOMER` becomes a fully-provisioned `SELLER` (with an empty-but-ready storefront) or `INFLUENCER` (with a profile *and* a working referral code) in one atomic `@Transactional` step. **One naming quirk worth knowing cold**: `approve(Long applicationId, String adminEmail, ...)` — the parameter is *named* `adminEmail`, and `reviewedBy` is documented as storing the admin's identity, but the controller actually passes `authentication.getName()`, which (per the JWT design in Section 3) is the admin's **numeric user ID as a string**, not their email. The `reviewedBy` column will contain something like `"7"`, not `"admin@example.com"` — a misleading variable/field name, not a functional bug (it still uniquely identifies the admin), but exactly the kind of naming inconsistency worth fixing if you extend this code, and a sharp interview answer if asked "is everything in this codebase perfectly named?"

### `seller/` package

**`SellerController.java`** — `/api/sellers`; public list/nearby/get-by-id, and two self-service endpoints (`PATCH /profile`, `PATCH /toggle-open`) that **also use the broken `@AuthenticationPrincipal UserDetails`** pattern — the third and final place this bug appears (Section 11), and arguably the most damaging one, since it means **no seller can currently update their own storefront profile or toggle their shop open/closed through this API** without first fixing that bug.

**`SellerProfile.java`** — entity (Section 4); `@OneToOne` to `User`.

**`SellerProfileDto.java`** — merges fields from both `User` (name, email, lat/long) and `SellerProfile` (shop name/bio/etc.) into one flat object, plus two **computed-on-read** fields: `productCount` (a live count, not stored) and `profileCompleteness` (a 0–100 score built from five 20-point checks — shop name, bio, banner image, delivery radius set, and "has at least one product" — a nice small piece of UX-oriented business logic worth being able to explain: it's not stored anywhere, it's recalculated every time a seller's profile is requested).

**`SellerProfileRepository.java`** — `findByUserId`/`existsByUserId`.

**`SellerService.java`** — `getNearbySellers` is a straightforward in-memory filter over *every* active seller (`findByRoleAndIsActiveTrue` then a Java `.filter()` using Haversine distance) — fine at the scale of "a few hundred sellers," but worth flagging as something that would need a proper geospatial database index (e.g. PostGIS) or pre-computed geohash bucketing if Bakeaura ever had tens of thousands of sellers; doing a full table scan + distance calc per request doesn't scale linearly forever. `createProfileForNewSeller` is the seller-side counterpart to `InfluencerProfileService.createProfileForNewInfluencer`, called from the same `RoleApplicationService.approve` step.

**`UpdateSellerProfileDto.java`** — partial-update DTO, same null-means-unchanged pattern as `InfluencerProfileUpdateRequest`.

### `user/` package

**`ChangeEmailRequest.java`/`ChangePasswordRequest.java`** — validated request bodies; `ChangeEmailRequest` requires the user's **current password** as proof of identity before allowing an email change request — you can't hijack an account just by knowing it's logged in on an unattended device; you still need the password to redirect where the account's communications go.

**`User.java`** — the central entity (Section 4); the one with deliberately no JPA relationship annotations at all.

**`UserController.java`** — `/api/users/me` (get/update profile, change password), `/api/users/me/change-email` (kicks off the two-step flow — Section 6). The actual *confirmation* endpoint for that flow, `GET /api/auth/verify-email-change`, lives in `AuthController` instead, not here — a small but real cross-package coupling, because email-verification-link endpoints are grouped with the other `/api/auth/verify-*` links rather than under `/api/users`.

**`UserDto.java`** — the public profile shape; deliberately omits `password`, `phone`, `bio`, `profileImageUrl`, and every email-verification/email-change internal field — exactly the kind of information that *must never* leave the server, which is the whole point of having a DTO distinct from the entity (Section 9).

**`UserProfileUpdateRequest.java`** — `name` + `latitude`/`longitude` only; notably you **cannot** change your own `role` or `email` through this endpoint — role changes go through the application/approval flow, and email changes go through the two-step verified flow; this endpoint is intentionally narrow.

**`UserRepository.java`** — `findByEmail`/`existsByEmail` (used everywhere auth-related), `findByRoleAndIsActiveTrue`/`findByRole` (used by seller/influencer directories and the admin user list), `findByEmailVerificationToken`/`findByPendingEmailToken` (the two separate token lookups for the two separate verification flows — registration verification and email-change verification — kept as distinct columns/tokens rather than reused, so completing one flow can never accidentally also complete the other).

**`UserService.java`** — `requestEmailChange` is the first half of the two-step email change (Section 6): verifies the current password, checks the new email isn't already taken, generates a fresh token with a 24-hour expiry, stores it as `pendingEmail`/`pendingEmailToken` (note: **the live `email` column is untouched** at this point — login, JWT subject, everything keeps working with the old email throughout), and emails the *new* address (not the old one) the confirmation link — because the whole point is proving the user actually controls that new inbox. `confirmEmailChange` is the second half: looks up by the pending token, checks expiry, and **only then** overwrites `email` with `pendingEmail` and clears all three pending fields.

### `wallet/` package

**`WalletService.java`** — see Section 4 and Section 6 for the full "no stored balance, ever" reasoning. `credit`/`debit` each just insert one new row; `debit` additionally re-checks the live balance covers the amount before inserting (a second guard beyond `PayoutRequestService`'s own check, since `WalletService` doesn't know or trust *why* it's being asked to debit — defence in depth again). `getBalance`/`getTransactionHistory` are both read-only.

**`WalletTransaction.java`** — entity (Section 4); `@PrePersist` stamps `createdAt`.

**`WalletTransactionRepository.java`** — `calculateBalance`'s `@Query` is the one line of SQL that makes the entire append-only-ledger design work: `SUM(CASE WHEN type = :credit THEN amount ELSE -amount END)`, wrapped in `COALESCE(..., 0)` so an influencer with zero transactions gets `0`, not `NULL`.

### `websocket/` package

**`OrderStatusMessageDto.java`** — the payload pushed to `/topic/order/{orderId}`: `orderId`, `status`, a human-readable `message`, `timestamp`.

**`OrderTrackingController.java`** — a `@Controller` (not `@RestController` — STOMP message-mapped methods, not HTTP-mapped ones) handling `/app/order/{orderId}/join`: when a client sends a "join" message, the server echoes back a confirmation to `/topic/order/{orderId}`. This is purely a connection-confirmation nicety — the client doesn't strictly need to send this to *receive* status updates (any client subscribed to that topic gets broadcasts regardless), it's just a "yes, you're connected" handshake.

**`OrderTrackingService.java`** — `handleOrderCreated` (an `@EventListener`) immediately broadcasts a `PENDING` status the instant an order is created, so a client that subscribes right after redirecting to the order-tracking page sees an initial state without waiting for the first real status change. `broadcastStatusUpdate` is called from both `OrderService.updateStatus`/`cancelOrder` *and* `PaymentService` (on payment capture, confirming the order) — multiple callers, one shared broadcast method, each producing a friendly per-status message via a `switch` expression.

### Test files (`backend/src/test/java/...`)

Twelve test files exist, covering `AuthService`, `JwtAuthFilter`, `Cart` (controller + service), `Category` (controller + service), `MapService`, `Order` (controller + service), `Payment` (controller + service), plus the bare `BakeauraBackendApplicationTests` (just confirms the Spring context loads — a useful canary that catches "the app doesn't even start" before anything else). **Critical, verified finding**: most of these tests are **stale relative to the current source code** and would fail to compile if you ran `mvn test` today — see Section 11 for the exact line-by-line evidence (constructor signature mismatches, email-string vs. numeric-ID mismatches). `MapServiceTest` and the `Category` tests are the exceptions — they still match the current code, because `MapService` and `CategoryService` never went through the email→numeric-ID migration that broke the others.

---

## SECTION 6 — KEY PATTERNS AND DECISIONS

### 1. JWT with numeric user ID as subject (not email)

**Analogy**: a library card with a barcode number printed on it, not your name. The librarian (every controller) scans the barcode and looks you up by number — fast, and it never breaks if you legally change your name (i.e., change your email).

**The code**: `JwtUtil.generateAccessToken(Long userId, Role role)` → `.subject(String.valueOf(userId))`. Every controller does `Long.parseLong(authentication.getName())`.

**Why this over email-as-subject**: (1) primary-key lookups (`findById`) are faster than indexed-but-still-secondary-key lookups (`findByEmail`) on every single authenticated request; (2) if a user changes their email (the two-step flow in pattern #13), their existing, still-valid access tokens keep working without needing to be reissued — the ID never changes, only the email does; (3) emails can theoretically be reused/recycled in edge cases (deleted account, new account, same email) — IDs never are, since they're auto-incrementing and never reused once assigned.

**What could go wrong if implemented badly**: exactly what happened here, partially — three controllers (`SellerController`, `ReelController`, `InfluencerCollaborationController`) were written *assuming* the principal would be a `UserDetails` object whose `getUsername()` returns something — a holdover from a more "textbook" Spring Security setup that this app's actual `JwtAuthFilter` never implements. Mixing two different "who is the caller" conventions in one codebase is the exact failure mode to avoid (Section 11 has the full damage report).

### 2. Spring `ApplicationEvent`s for order creation — publish/subscribe decoupling

**Analogy**: a notice board outside a bakery's kitchen. The instant a new order ticket is pinned to the board (published), the cashier, the delivery dispatcher, and the inventory clerk all independently notice and act — the kitchen never has to personally walk over and tell each of them; it just pins the notice once.

**The code**: `OrderService.createOrder` ends with `eventPublisher.publishEvent(new OrderCreatedEvent(this, savedOrder, customerEmail, referralCode))`. Three completely separate `@EventListener` methods react: `PaymentService.handleOrderCreated` (creates the Razorpay order + pending payment), `CartService.handleOrderCreated` (clears the cart), `NotificationService.handleOrderCreated` (notifies the seller), `OrderTrackingService.handleOrderCreated` (broadcasts initial `PENDING` status over WebSocket).

**Why this over calling each service directly from `OrderService`**: `OrderService` would otherwise need to know about (and inject) `PaymentService`, `CartService`, `NotificationService`, *and* `OrderTrackingService` just to create an order — a tangle of cross-feature dependencies in exactly the package that's supposed to be the cleanest. With events, `OrderService` knows about **zero** of its listeners. You could delete `CartService`'s listener entirely and `OrderService` would not need a single line changed.

**Important nuance**: by default, Spring's `ApplicationEventPublisher` calls listeners **synchronously, on the same thread, inside the same transaction** as the publisher — this is *not* a message queue, despite the "decoupled" framing. If `PaymentService.handleOrderCreated` throws, that exception propagates straight back up into `OrderService.createOrder` and rolls back the whole order. This is why `OrderService.createOrder` saves the order *twice* (once before publishing, once after) — the event listener mutates the order object (attaching `razorpayOrderId`) before control returns, and that needs a second flush.

**What could go wrong if implemented badly**: forgetting this is synchronous-by-default is the single most common mistake — someone assuming event listeners "fire and forget" and being surprised when a slow or failing listener blocks (or breaks) the original request.

### 3. Circuit breaker states — CLOSED, OPEN, HALF-OPEN

**Analogy**: a household circuit breaker. **CLOSED** = electricity flows normally (your calls to Razorpay/Cloudinary go through as usual). If too many things draw too much current too fast (errors exceed the configured failure-rate threshold within a sliding window), the breaker **trips to OPEN** = it stops sending power entirely for a cooldown period — every call during this time fails *instantly* via the fallback method, without even attempting the real network call, because the breaker has decided "this circuit is clearly broken, don't even try." After the wait duration elapses, it moves to **HALF-OPEN** = it lets a small, limited number of test calls through to see if the underlying problem has cleared up; if those succeed, it flips back to CLOSED (normal operation resumes); if they still fail, it trips back to OPEN for another cooldown.

**The code**: `application.yml`'s `resilience4j.circuitbreaker.instances` block configures three named breakers — `razorpay` (10-call sliding window, 50% failure threshold, 60s open-state wait, 3 half-open test calls), `cloudinary` (same shape), `gemini` (configured but unused — Section 3). `@CircuitBreaker(name = "razorpay", fallbackMethod = "createRazorpayOrderFallback")` on `PaymentService.createRazorpayOrder` is the actual wiring; the fallback simply throws a friendly "temporarily unavailable" error instead of whatever raw exception the SDK would have thrown.

**Why this over plain try/catch**: a try/catch on a single call doesn't protect you from the *pattern* of repeated failures — every single request would still attempt the slow/failing call and wait out its own timeout. A circuit breaker tracks failures *across calls* and, once it trips, fails new calls **immediately**, protecting your own thread pool from filling up with requests all blocked waiting on a dead dependency.

**What could go wrong if implemented badly**: a threshold set too sensitively trips the breaker on normal, brief blips (false positives, annoying users for no real reason); a threshold set too loosely never trips at all, defeating the entire purpose; forgetting to implement a correct fallback signature (exact parameters + trailing `Throwable`) means Resilience4j can't find your fallback at all and the original exception propagates anyway.

### 4. Token bucket rate limiting with Bucket4j — how tokens refill

**Analogy**: a bucket that holds, say, 5 tokens. Every request to a protected endpoint removes one token. The bucket refills to full again after a fixed time window (here, every 1 minute) — it's a "greedy refill," meaning tokens trickle back continuously over that window rather than all snapping back at once at the minute mark.

**The code**: `RateLimitFilter.resolveBucket` creates (or fetches, via `computeIfAbsent` — thread-safe, no double-creation race) one `Bucket` per client IP per category, using `Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1))`. `doFilterInternal` checks the request's method+path against three real categories (login, register, payment-POSTs) and one currently-dead category (`/api/v1/ai/**`), and calls `bucket.tryConsume(1)` — `true` lets the request through, `false` immediately returns `429 Too Many Requests` without ever reaching the controller.

**Why this over a fixed window counter** ("max 5 requests per calendar minute"): a fixed window has a burst problem at the boundary — a client could send 5 requests at 11:59:59 and another 5 at 12:00:01, getting 10 requests through in 2 real seconds. A token bucket with greedy refill smooths this out.

**What could go wrong if implemented badly**: this implementation is **in-memory and per-instance** — if Bakeaura ever ran two app instances behind a load balancer, each instance would have its own independent bucket per IP, effectively *doubling* (or N-ing) the real allowed rate, because a determined client could be load-balanced across instances. A production-grade version would back the buckets with Redis (Bucket4j supports this) so all instances share one counter per IP.

### 5. Redis caching with `@Cacheable`/`@CacheEvict` — invalidation strategy

**Analogy**: a sticky note on your monitor with a frequently-needed phone number written on it. You glance at the sticky note instead of looking the number up in the phone book every time (the cache hit); but the moment that number changes, you must tear up the old sticky note (`@CacheEvict`) — otherwise you'll keep dialling a wrong, stale number forever.

**The code**: `CategoryService`, `ProductService`, `SellerService`, and `ReviewService.getSummary` all use this. The invalidation strategy is uniformly **blunt-but-correct**: every write method evicts the *entire* cache region (`allEntries = true`) rather than trying to surgically evict just the one changed key. `ReviewService` is the one exception with surgical, SpEL-computed key eviction (`#result.sellerId`).

**Why blunt eviction over surgical eviction here**: for categories and products, the *read* volume vastly outweighs the *write* volume (browsing happens constantly; creating/editing a product is comparatively rare), so occasionally re-warming the whole cache on a write is a perfectly acceptable cost for the simplicity of never having to enumerate every possible cache key a write might affect (e.g. a product update could affect `"all products"`, `"products by category X"`, `"products by seller Y"`, several search-keyword caches... surgically evicting all of those correctly is genuinely hard to get right, and getting it wrong means serving stale data, which is worse than the minor cost of a blunt full-region wipe).

**What could go wrong if implemented badly**: forgetting `@CacheEvict` on even one write path (e.g. if a new "bulk update products" feature were added without it) means users would see stale product data for up to 10 minutes (the configured TTL) after an edit — a classic, hard-to-notice-in-testing cache bug.

### 6. Wallet as append-only transaction log — why never update, only insert

**Analogy**: a bank statement, not a single "current balance" sticky note. Banks never *edit* a past transaction to "fix" your balance — they post a new transaction (a correction, a reversal) and your balance is always *the sum of everything that ever happened*. This is also literally how double-entry bookkeeping and blockchain ledgers work, for the same reason.

**The code**: `wallet_transactions` has no `balance` column. `WalletService.getBalance` always recomputes from `SUM(CREDIT) - SUM(DEBIT)` via `WalletTransactionRepository.calculateBalance`. `credit`/`debit` only ever `INSERT`, never `UPDATE`.

**Why this over a stored, incrementally-updated balance column**: a stored balance can drift from "the truth" through any of: a bug that updates the balance but forgets to also insert the ledger row (or vice versa), a crash between the two writes if they aren't perfectly atomic, a manual SQL `UPDATE` in production during an incident that someone forgets to also log, or simple arithmetic bugs (`+=` vs `-=` typos) that compound silently over months. With an append-only ledger, **the balance has no independent existence to drift from** — it is, by definition, always exactly what the transaction history says it is. You can also always answer "how did the influencer get to this balance" by reading the history — a stored balance alone can't explain itself.

**What could go wrong if implemented badly**: this pattern only protects you if you actually never update or delete a row after insert. If some future feature added an "edit transaction" admin tool, the entire guarantee evaporates.

### 7. Optimistic locking with `@Version` — race condition prevention

**Analogy**: two people editing the same shared Google Doc paragraph at the same time without realizing it. Optimistic locking is like the doc saying "someone else already changed this since you last loaded it — please reload and reapply your edit," rather than silently letting the second save clobber the first person's change.

**The code**: `Product.version` (`@Version private Long version;`). Hibernate automatically includes `version` in the `WHERE` clause of every `UPDATE` and increments it on success. If `Product` had no `@Version` field, here's the exact race: Customer A and Customer B both load the same product showing `stockQuantity = 1`. Both pass the "is there enough stock" check (`1 >= 1`). Both then save, both decrementing to `0` — but **two units got sold from a stock of one**, an overselling bug. With `@Version`, the second `UPDATE` (whichever one runs second) fails its `WHERE id=? AND version=?` clause (the first update already bumped the version), Hibernate throws `OptimisticLockException`, and the second sale is rejected/retried instead of silently succeeding incorrectly.

**Why this over pessimistic locking** (`SELECT ... FOR UPDATE`, which physically blocks other transactions from even reading the row until the first one finishes): optimistic locking has **zero cost** in the overwhelmingly common case where there's no actual contention (most products aren't being bought by two people in the same millisecond) — it only pays a cost (a thrown exception, requiring a retry) in the rare case contention *actually* happens. Pessimistic locking pays a blocking cost on every single access, contended or not.

**What could go wrong if implemented badly**: forgetting to actually *catch* `OptimisticLockException` somewhere and retry or surface a clean error means the customer just sees a raw `500` when this (rare, but real) race occurs — worth noting this app doesn't currently have an explicit catch for it in `OrderService`/`CartService`, meaning it would currently surface as `GlobalExceptionHandler`'s generic 500, not a friendly "someone else just bought the last one, please refresh."

### 8. Delivery address denormalization — the snapshot pattern

**Analogy**: a printed paper receipt versus a hyperlink. A hyperlink ("see the address on file") breaks or changes meaning if the destination page is later edited or deleted. A printed receipt freezes the exact address as it was *at the moment of purchase*, forever — even if you later move house.

**The code**: `Order.deliveryAddress`/`deliveryLatitude`/`deliveryLongitude` are plain copied fields, **not** a foreign key to `addresses`. `OrderItem.priceAtPurchase` is the same idea applied to price.

**Why copy instead of reference**: orders are historical receipts. If a customer edits or deletes a saved address after placing an order, or a seller changes a product's price next week, every *past* order must continue to show exactly what was true *at the time it happened* — that's not just nice-to-have, it's often a legal/audit requirement for anything resembling a financial transaction record.

**What could go wrong if implemented badly**: the opposite mistake — storing a foreign key to the live `addresses`/`products` row instead of a snapshot — would mean editing your saved home address could silently rewrite the delivery address shown on a three-month-old, already-delivered order, which is both confusing and potentially a real customer-trust problem ("the system changed my order after the fact").

### 9. `@Async` thread pool for emails and SMS — non-blocking response

**Analogy**: handing a letter to a mail clerk and walking away immediately, versus standing at the post office counter until the letter is physically sealed, stamped, and on the truck. The customer doesn't need to wait for "did the confirmation email actually leave Gmail's servers" before getting their own HTTP response back.

**The code**: `AsyncConfig.@EnableAsync` turns the feature on; `application.yml`'s `spring.task.execution.pool` configures a thread pool (4 core threads, 8 max, 100-deep queue); every `EmailService`/`SmsService` send method is `@Async`. When `OrderService.updateStatus` calls `emailService.sendOrderConfirmationEmail(...)`, that call returns **immediately** (Spring proxies the call onto a pool thread behind the scenes), and `updateStatus` continues on to broadcast the WebSocket update and return its HTTP response without waiting for the SMTP handshake to even start.

**Why this over calling them synchronously**: a slow or hung SMTP/SMS provider would otherwise directly add that exact latency to *every* order-status-change API call — turning "the email server is having a bad day" into "the entire app feels slow," which is exactly the kind of coupling you want to avoid.

**What could go wrong if implemented badly**: `@Async` methods that throw exceptions **do not propagate that exception back to the caller** (the caller already moved on) — by default, an async method's exception is just logged by Spring's default `AsyncUncaughtExceptionHandler` and otherwise disappears. This app sidesteps that entirely by catching every exception *inside* `sendEmail`/`sendSms` and logging there — a defensive double-layer that doesn't rely on Spring's async exception handling at all, which is the right instinct even if slightly belt-and-suspenders.

### 10. Pre-order `minAdvanceDays` validation — business rule enforcement

**Analogy**: a bakery sign that says "custom wedding cakes require 7 days notice" — you can't walk in and demand one for tomorrow, no matter how much you want to pay.

**The code**: `Product.minAdvanceDays` (nullable — most products have no such restriction). `OrderService.createOrder`, only when `orderType == SCHEDULED`, loops every item, and if the product has a `minAdvanceDays` value, computes `earliestDate = today + minAdvanceDays` and rejects the order if `scheduledDeliveryDate` is before that.

**Why enforce this in the service layer, not just a UI hint**: a UI-only restriction is trivially bypassed by anyone calling the API directly (Postman, a script, a modified frontend) — and Bakeaura's whole stock/order pipeline assumes this constraint actually held when the order was accepted. Server-side enforcement is the only enforcement that counts as real.

**What could go wrong if implemented badly**: this check only runs for `orderType == SCHEDULED` — an `INSTANT` order for a `minAdvanceDays`-restricted product is **never checked against this rule at all** (there's no `scheduledDeliveryDate` to compare against). Whether that's intentional ("instant orders are by definition for ready-to-go items, so the rule doesn't apply") or an oversight is genuinely ambiguous from the code alone — a good question to ask if you were the engineer extending this feature.

### 11. Feed ranking algorithm — weighted scoring normalisation

**Analogy**: judging a baking competition on four criteria (how recent, how popular by likes, how popular by views, how good is the baker's overall reputation), where each criterion is first rescaled to a common 0-to-1 "score" (normalisation) before being combined with different importance weights — otherwise a raw "viewCount of 50,000" would completely swamp a raw "rating of 4.8," even if rating should matter more.

**The code** (`ContentService.getRankedFeed`): for every `ACTIVE` reel, compute `normalizedRecency = 1 - (ageHours / maxAgeHoursAcrossAllReels)` (newer = closer to 1, oldest in the current batch = exactly 0 — a *linear* decay, not exponential), `normalizedLikes = likeCount / maxLikesInBatch`, `normalizedViews = viewCount / maxViewsInBatch`, `ratingScore = sellerAverageRating / 5.0`. Final `score = 0.40×recency + 0.25×likes + 0.20×views + 0.15×rating`, then sort descending.

**Why normalise against the *current batch's* max, not a fixed constant**: this makes the formula self-adjusting — if Bakeaura's most-liked reel ever has 50 likes or 50,000 likes, "the most popular reel right now" always scores a perfect `1.0` on that dimension, and everything else is judged relative to it, rather than against some made-up fixed denominator that would need constant manual tuning as the platform grows.

**What could go wrong if implemented badly**: dividing by a batch max of `0` would throw a divide-by-zero/NaN — the code guards this with `.orElse(1L)` (and `.orElse(1.0)` for likes via `max()` defaults), ensuring a single reel with zero likes still computes a valid `0/1 = 0` instead of `0/0 = NaN`. This formula also recomputes from scratch on every single request — fine at the current scale (presumably dozens to low hundreds of reels), but would need either pagination-aware ranking or a precomputed/cached score if the reel count grew into the thousands.

### 12. Haversine formula — pure Java distance calculation

**The math, the analogy, and why it's *not* paired with a Google Maps fallback** are all covered fully in Section 3. The one thing worth re-emphasizing here: this is the textbook example of "favour a free, fast, deterministic, dependency-free calculation over an external API call when 'good enough' accuracy is genuinely good enough for the business need" — a delivery-radius gate and a rough ETA don't need turn-by-turn road routing precision.

### 13. Two-step email change verification — security flow

**Analogy**: changing your address with the post office by mailing a confirmation postcard to your *new* address and waiting for you to mail it back, rather than just believing whatever address you verbally tell them over the phone — proves you actually *receive mail* at the new address before any of your future mail gets redirected there.

**The code**: Step one, `UserService.requestEmailChange` — verify current password, check new email isn't taken, generate a token + 24h expiry, store as `pendingEmail`/`pendingEmailToken`/`pendingEmailTokenExpiry` **without touching the live `email` column**, and send the confirmation link to the **new** address. Step two, `UserService.confirmEmailChange` (reached via `GET /api/auth/verify-email-change?token=...`) — look up by token, check expiry, **then and only then** copy `pendingEmail` into `email` and clear the three pending fields.

**Why two steps instead of just updating `email` immediately on request**: if email changes took effect immediately, an attacker who briefly gets hold of a logged-in session (or guesses/steals the current password through some other means) could redirect a victim's account to an email address *the attacker* controls, then use "forgot password" to fully take over the account — without ever needing to receive anything at the new address themselves. Requiring a successful click-through on a link sent to the new address proves the person requesting the change can actually read mail there, which an attacker typically can't.

**What could go wrong if implemented badly**: forgetting to require the current password on the *request* step would mean anyone with a stolen, still-valid access token (e.g. from an XSS attack or a leaked token) could kick off an email change even without knowing the account's password — the password check here is a deliberate extra layer specifically against that scenario.

### 14. Review system linked to delivered orders — data integrity enforcement

**Analogy**: only letting someone leave a restaurant review after they've actually eaten there and paid the bill, not before they've even sat down.

**The code**: `ReviewService.createReview`'s three checks — `order.getCustomer().getId().equals(customer.getId())` (you reviewed your own order), `order.getStatus() == OrderStatus.DELIVERED` (it actually happened), `!reviewRepository.existsByCustomerAndOrder(...)` (no double-reviewing) — backed at the database level by the `@UniqueConstraint(columnNames = {"customer_id", "order_id"})` on the `reviews` table, so even a application-layer bug couldn't actually insert a duplicate row; the database itself would reject it with a constraint violation (which `GlobalExceptionHandler`'s `DataIntegrityViolationException` handler turns into a clean 400, see Section 5).

**Why tie reviews to *orders* rather than just letting any customer review any seller freely**: it guarantees every review on the platform represents a real, completed transaction — protecting against fake reviews from people who never actually bought anything, and against a single bad experience generating five duplicate angry reviews.

**What could go wrong if implemented badly**: skipping the `DELIVERED` check would let customers review (and potentially badmouth) a seller before the seller even had a chance to fulfil the order — both unfair to the seller and a meaningless signal to other customers.

---

## SECTION 7 — ALL API ENDPOINTS

Endpoints marked **⚠ BROKEN** use `@AuthenticationPrincipal UserDetails`, which resolves to `null` at runtime in this codebase (Section 11) — calling them currently throws a `NullPointerException`, returned to the client as a generic `500`.

### Auth — `/api/auth` (all PUBLIC)

| Method | Path | Body | Response `data` | Rules |
|---|---|---|---|---|
| POST | `/register` | `name, email, password` | `AuthResponse` | Always creates role `CUSTOMER`; sends async verification email; issues tokens immediately (verification not required to log in). |
| POST | `/login` | `email, password` | `AuthResponse` | Rejects wrong password or inactive account. |
| POST | `/refresh` | `refreshToken` | `AuthResponse` (new pair) | Validates token type is `refresh`, matches the one stored in Redis, then **rotates** (old one replaced). |
| POST | `/logout` | `refreshToken` | `null` | Deletes the matching Redis entry. |
| GET | `/verify-email?token=` | – | `null` | Rejects expired tokens. |
| GET | `/verify-email-change?token=` | – | `null` | Completes step 2 of the email-change flow (Section 6 #13). |

### Users — `/api/users` (ANY AUTHENTICATED)

| Method | Path | Body | Response | Rules |
|---|---|---|---|---|
| GET | `/me` | – | `UserDto` | |
| PATCH | `/me` | `name, latitude, longitude` | `UserDto` | Cannot change role or email here. |
| PATCH | `/me/password` | `currentPassword, newPassword` | `null` | Verifies current password first. |
| POST | `/me/change-email` | `newEmail, currentPassword` | `null` | Starts the two-step flow; live email unchanged until confirmed. |

### Addresses — `/api/addresses` (ANY AUTHENTICATED, owner-enforced)

| Method | Path | Body | Response | Rules |
|---|---|---|---|---|
| GET | `/` | – | `AddressDto[]` | Default address sorted first. |
| POST | `/` | `label, addressLine, latitude, longitude, defaultAddress` | `AddressDto` | |
| PUT | `/{id}` | same as create | `AddressDto` | 403 if not owner. |
| PATCH | `/{id}/default` | – | `AddressDto` | Clears every other address's default flag first. |
| DELETE | `/{id}` | – | `null` | 403 if not owner. |

### Products — `/api/products`

| Method | Path | Params/Body | Access | Rules |
|---|---|---|---|---|
| GET | `/` | – | PUBLIC | Only `isAvailable = true` products. |
| GET | `/search` | `keyword` | PUBLIC | Case-insensitive name `LIKE`. |
| GET | `/filter` | `keyword, categoryId, sellerId, minPrice, maxPrice, available, page, size, sort` | PUBLIC | Dynamic JPA Specification; `available` defaults `true` if omitted. |
| GET | `/category/{categoryId}` | – | PUBLIC | |
| GET | `/seller/{sellerId}` | – | PUBLIC | |
| GET | `/{id}` | – | PUBLIC | 404 if not found (returns plain `404`, not wrapped in `ApiResponse`'s usual error shape — see `Optional.orElse(ResponseEntity.notFound().build())`). |
| POST | `/` | `name, description, price, stockQuantity, categoryId, imageUrl` | SELLER | |
| PUT | `/{id}` | same | SELLER, owner | 403 (as a `RuntimeException`, not `AccessDeniedException` — see Section 11) if not owner. |
| DELETE | `/{id}` | – | SELLER, owner | Same caveat. |

### Categories — `/api/categories`

| Method | Path | Body | Access | Rules |
|---|---|---|---|---|
| GET | `/` , `/{id}` | – | PUBLIC | Cached 10 min. |
| POST | `/` | `name, description, imageUrl` | ADMIN | Rejects duplicate name (case-insensitive). |
| PUT | `/{id}` | same | ADMIN | |
| DELETE | `/{id}` | – | ADMIN | Rejects if any product still uses it. |

### Cart — `/api/cart` (CUSTOMER only)

| Method | Path | Params | Rules |
|---|---|---|---|
| GET | `/` | – | Self-repairing read (drops dead/unavailable/out-of-stock items, clamps over-stock quantities). |
| POST | `/items/{productId}` | `quantity` (default 1, ≥1) | Merges into existing line if already in cart. |
| PATCH | `/items/{productId}` | `quantity` (≥0) | `0` removes the item. |
| DELETE | `/items/{productId}` | – | |
| DELETE | `/` | – | Clears whole cart. |

### Orders — `/api/orders`

| Method | Path | Params/Body | Access | Rules |
|---|---|---|---|---|
| POST | `/` | `sellerId, items[{productId,quantity}], deliveryAddress, deliveryLatitude, deliveryLongitude, orderType, scheduledDeliveryDate?, referralCode?` | CUSTOMER | Validates seller role, delivery radius, per-product ownership/availability/stock, and `minAdvanceDays` for `SCHEDULED` orders; publishes `OrderCreatedEvent`. |
| POST | `/from-cart` | `sellerId, deliveryAddress, deliveryLatitude, deliveryLongitude, orderType, scheduledDeliveryDate?, referralCode?` | CUSTOMER | Builds the items list from the current cart; rejects if cart empty. |
| PATCH | `/{orderId}/status` | `status` query param | SELLER or ADMIN | Enforces the state machine; triggers email/SMS/WebSocket/notification side effects per status. |
| GET | `/my-orders` | `page, size` | CUSTOMER | Paginated, newest first. |
| GET | `/seller-orders` | `status?, page, size` | SELLER | |
| GET | `/{orderId}` | – | ANY AUTHENTICATED | 403 unless caller is the order's customer, seller, or an admin. |
| POST | `/{orderId}/cancel` | – | CUSTOMER, owner | Only from `PENDING`/`CONFIRMED`. |

### Payments — `/api/payments`

| Method | Path | Body/Header | Access | Rules |
|---|---|---|---|---|
| POST | `/webhook` | raw JSON + `X-Razorpay-Signature` header | PUBLIC (signature-verified) | Idempotent against duplicate `payment.captured`; handles `payment.failed` too. |
| GET | `/config` | – | PUBLIC | Returns `keyId` + `currency` only — never the secret. |
| POST | `/verify` | `razorpayOrderId, razorpayPaymentId, razorpaySignature` | ANY AUTHENTICATED (service checks caller is the order's customer) | Idempotent against being called after the webhook already captured it. |
| GET | `/order/{orderId}` | – | ANY AUTHENTICATED, object access enforced | |

### Sellers — `/api/sellers`

| Method | Path | Params | Access | Rules |
|---|---|---|---|---|
| GET | `/` | – | PUBLIC | Active sellers only. |
| GET | `/nearby` | `latitude, longitude, radius=10.0` | PUBLIC | In-memory Haversine filter over all active sellers. |
| GET | `/{id}` | – | PUBLIC | |
| PATCH | `/profile` | `shopName, shopBio, deliveryRadiusKm, bannerImageUrl` | SELLER | **⚠ BROKEN** — NPE on `UserDetails`. |
| PATCH | `/toggle-open` | – | SELLER | **⚠ BROKEN** — NPE on `UserDetails`. |

### Favorites — `/api/favorites` (ANY AUTHENTICATED)

| Method | Path | Response | Rules |
|---|---|---|---|
| GET | `/` | `ProductDto[]` | |
| POST | `/{productId}` | `ProductDto[]` (full updated list) | Idempotent — favouriting twice is a no-op. |
| DELETE | `/{productId}` | `ProductDto[]` | |
| GET | `/{productId}` | `{ favorite: boolean }` | |

### Reviews

| Method | Path | Body | Access | Rules |
|---|---|---|---|---|
| GET | `/api/sellers/{sellerId}/reviews` | – | PUBLIC | |
| GET | `/api/sellers/{sellerId}/reviews/summary` | – | PUBLIC | `coalesce`d average (0 if no reviews). |
| POST | `/api/orders/{orderId}/reviews` | `rating (1-5), comment` | ANY AUTHENTICATED | Must own the order, order must be `DELIVERED`, one review per order. |
| DELETE | `/api/orders/{orderId}/reviews` | – | ANY AUTHENTICATED | |

### Notifications — `/api/notifications` (ANY AUTHENTICATED)

| Method | Path | Params | Response |
|---|---|---|---|
| GET | `/` | `page=0, size=20` | `Page<NotificationDto>` |
| GET | `/unread-count` | – | `{ unreadCount: long }` |
| PATCH | `/{id}/read` | – | `NotificationDto` (owner-enforced) |
| PATCH | `/read-all` | – | `null` |

### Admin — `/api/admin` (ADMIN only, class-level)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/dashboard` | – | `AdminDashboardDto { users, products, orders, payments, categories }` |
| GET | `/users?role=` | – | `UserDto[]` |
| PATCH | `/users/{id}/status` | `{ active }` | `UserDto` |
| PUT | `/users/{id}/role` | `{ role }` | `UserDto` |
| DELETE | `/users/{id}` | – | `null` |

### Role Applications

| Method | Path | Body | Access | Rules |
|---|---|---|---|---|
| POST | `/api/role-applications` | `requestedRole (SELLER\|INFLUENCER), message` | ANY AUTHENTICATED | Rejects if already that role or already has a pending application for it. |
| GET | `/api/role-applications/me` | – | ANY AUTHENTICATED | |
| GET | `/api/admin/role-applications?status=` | – | ADMIN | |
| POST | `/api/admin/role-applications/{id}/approve` | `{ reviewNote }` | ADMIN | **Provisions** the seller/influencer profile (+ referral code) atomically. |
| POST | `/api/admin/role-applications/{id}/reject` | `{ reviewNote }` | ADMIN | |

### Influencers — `/api/influencer` (storefront) and `/api/influencers` (directory — same path as `InfluencersPage` consumes, served by `InfluencerProfileController`/`UserController` data, list endpoint not present as a separate controller in this codebase — see note below)

| Method | Path | Body | Access | Rules |
|---|---|---|---|---|
| GET | `/api/influencer/profile` | – | INFLUENCER | |
| PATCH | `/api/influencer/profile` | `niche, instagramUrl, youtubeUrl, followerCount` | INFLUENCER | Partial update — null fields ignored. |
| GET | `/api/influencer/profile/{userId}` | – | ADMIN | |

> **Note for accuracy**: the frontend's `influencersApi` calls `GET /influencers` and `GET /influencers/{id}` (a public directory of influencer *users*), and `SecurityConfig` does permit public GETs on `/api/influencers/**` — but no `@RestController` in this backend actually maps a class-level `/api/influencers` path. This is a genuine gap: the frontend's "browse influencers" feature has no real backend endpoint to call yet (it would currently 404), distinct from the `/api/influencer/profile` *self-service* endpoints above, which require the INFLUENCER role and serve a different purpose.

### Influencer Collaborations — `/api/collaborations`

| Method | Path | Body/Params | Access | Status |
|---|---|---|---|---|
| POST | `/request/{influencerId}` | `{ message }` (optional) | SELLER | **⚠ BROKEN** — NPE on `UserDetails`. |
| GET | `/incoming` | – | INFLUENCER | **⚠ BROKEN**. |
| GET | `/outgoing` | – | SELLER | **⚠ BROKEN**. |
| PATCH | `/respond/{sellerId}` | `status` query param | INFLUENCER | **⚠ BROKEN**. |

### Custom Order Requests

| Method | Path | Params | Access | Rules |
|---|---|---|---|---|
| POST | `/api/custom-orders` | `sellerId, designBrief, occasion, serves, budgetMin, budgetMax` | CUSTOMER | Rejects a second pending request to the same seller. |
| GET | `/api/custom-orders/my-requests` | – | CUSTOMER | |
| GET | `/api/seller/custom-orders` | – | SELLER | |
| GET | `/api/seller/custom-orders/pending` | – | SELLER | |
| PUT | `/api/seller/custom-orders/{id}/accept` | – | SELLER, owner | Only from `PENDING`. |
| PUT | `/api/seller/custom-orders/{id}/reject` | – | SELLER, owner | Only from `PENDING`. |
| PUT | `/api/seller/custom-orders/{id}/quote` | `quote` | SELLER, owner | Only from `PENDING`; sets status `QUOTED`. |

### Influencer Payouts

| Method | Path | Params | Access | Rules |
|---|---|---|---|---|
| POST | `/api/influencer/payout` | `amount, upiId` | INFLUENCER | Rejects if already has a pending request or insufficient wallet balance. |
| GET | `/api/influencer/payout/history` | – | INFLUENCER | |
| GET | `/api/admin/payout/pending` | – | ADMIN | |
| PUT | `/api/admin/payout/{id}/approve` | – | ADMIN | Debits the wallet for real. |
| PUT | `/api/admin/payout/{id}/reject` | `note` | ADMIN | |

### Reels — `/api/reels`

| Method | Path | Body | Access | Status |
|---|---|---|---|---|
| POST | `/upload` | multipart `video` + `caption` | SELLER or INFLUENCER | **⚠ BROKEN** — NPE on `UserDetails`. |
| GET | `/feed` | `page=0, size=10` | PUBLIC | Chronological, not ranked. |
| GET | `/seller/{sellerId}` | – | PUBLIC | |

### Content Feed (ranked)

| Method | Path | Access | Rules |
|---|---|---|---|
| GET | `/api/content/feed` | PUBLIC | Returns the weighted-score-ranked reel feed (Section 6 #11). **Ignores** any `type`/`q`/`sellerId`/`page`/`size` query params the frontend may send. |

### WebSocket (STOMP over `/ws`, SockJS fallback)

| Direction | Destination | Payload | Notes |
|---|---|---|---|
| Client → Server | `/app/order/{orderId}/join` | – | Connection-confirmation handshake (optional). |
| Server → Client | `/topic/order/{orderId}` | `OrderStatusMessageDto` | Order status changes. |
| Server → Client | `/topic/users/{userId}/notifications` | `NotificationDto` | Live notifications. |
| Server → Client | `/topic/reels/{sellerId}` | `ReelResponseDTO` or `{reelId, status:"FAILED", error}` | Reel upload processing result. |

All three broker topics are currently **public** at the WebSocket layer (`/ws/**` is `permitAll()` in `SecurityConfig`, and STOMP destinations have no per-user/per-order subscription authorization) — see Section 11.

---

## SECTION 8 — SECURITY FLOW — REQUEST LIFECYCLE

Trace exactly what happens for, say, `GET /api/orders/42` arriving with `Authorization: Bearer eyJhbGci...`, from the moment it hits port 8080 to the moment a response leaves.

**Step 1 — TCP/HTTP arrives at embedded Tomcat (port 8080).** Tomcat parses the raw bytes into an `HttpServletRequest` object and hands it to the servlet filter chain. Nothing Spring-specific has happened yet — this is plain Java Servlet machinery.

**Step 2 — Servlet-level filters run first.** `CharacterEncodingFilter` ensures the request/response are read/written as UTF-8 (so accented characters, emoji, etc. survive intact) — you never touch this, it just runs.

**Step 3 — `RateLimitFilter` runs** (it's a generic Spring-Boot-registered `Filter` bean, separate from Spring Security's own chain — see Section 5's note on this). For `GET /api/orders/42`, the path doesn't match any of the rate-limited categories (login/register/payment-POST/AI), so it falls straight through to `filterChain.doFilter(...)` with zero delay.

**Step 4 — Spring Security's filter chain begins.** `SecurityContextHolderFilter` creates a brand-new, **empty** `SecurityContext` for this request — this is the literal mechanism behind "HTTP is stateless, every request starts with total amnesia" (Section 9). `HeaderWriterFilter` adds defensive response headers (`X-Content-Type-Options`, etc.) — invisible to your code, just happens. `CorsFilter` checks the `Origin` header against the allowed-origins list from `CorsConfig`; if this were a cross-origin browser request, this is where the preflight/actual-request CORS headers get added (Postman/cURL skip this entirely, since CORS is a *browser* enforcement mechanism, not a server-to-server one — see Section 9).

**Step 5 — `JwtAuthFilter.doFilterInternal` runs** (inserted via `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` in `SecurityConfig`). It reads the `Authorization` header, confirms it starts with `"Bearer "`, strips that prefix to get the raw token string. It calls `jwtUtil.isTokenValid(token)` — internally, this calls `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)`, which **recomputes the HMAC-SHA256 signature** over the token's header+payload using the server's secret key and compares it to the signature embedded in the token. If they don't match (tampered or signed with a different key) or the token is structurally malformed or expired, this throws internally and `isTokenValid` catches it and returns `false`. Assuming it's valid, it then confirms `isAccessToken(token)` (rejecting a refresh token used here as if it were an access token), extracts the `userId` (the JWT's `sub` claim, parsed back to a `Long`) and the `role` claim, builds a `UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))`, and calls `SecurityContextHolder.getContext().setAuthentication(authToken)` — **this is the exact moment "who is making this request" becomes known** to the rest of the app for the remainder of this one request.

**Step 6 — `ExceptionTranslationFilter`** is positioned to catch any `AuthenticationException`/`AccessDeniedException` thrown later in the chain and convert it into a clean `401`/`403` HTTP response instead of letting it surface as an ugly unhandled exception.

**Step 7 — `AuthorizationFilter`** checks the now-populated `SecurityContext` against the rules in `SecurityConfig.authorizeHttpRequests`. `GET /api/orders/42` doesn't match any of the explicit `permitAll()` patterns, so it falls to `.anyRequest().authenticated()` — since `SecurityContext` *does* have an authenticated principal (set in Step 5), this passes. (If `OrderController.getOrderById` itself carried a method-level `@PreAuthorize`, this is also where that expression gets evaluated — here it's `@PreAuthorize("isAuthenticated()")`, which is already satisfied.)

**Step 8 — `DispatcherServlet` routes to `OrderController.getOrderById`.** Spring resolves the `Authentication authentication` method parameter by pulling it straight out of the `SecurityContext` Step 5 populated. The controller calls `Long.parseLong(authentication.getName())` — `getName()` on a `UsernamePasswordAuthenticationToken` returns `principal.toString()`, and since the principal is the raw `Long` userId, this round-trips cleanly back to the same number that was embedded in the token at login time.

**Step 9 — `OrderService.getOrderById(42L, callerId)` runs.** It loads the order (with a `JOIN FETCH` to avoid N+1 — Section 9), loads the caller's `User` row, and checks `isCustomer || isSeller || isAdmin` — this is the **object-level authorization** check that role-based `@PreAuthorize` alone cannot express (knowing you're a `CUSTOMER` doesn't tell Spring Security whether you're *this specific order's* customer). If none match, it throws `AccessDeniedException`, which (per Step 6) becomes a clean `403`.

**Step 10 — Repository → Hibernate → PostgreSQL.** The JPQL `JOIN FETCH` query is translated to a single SQL `SELECT ... JOIN ...` by Hibernate, executed over a pooled HikariCP connection, and the `ResultSet` is mapped back into `Order`/`OrderItem`/`Product` Java objects.

**Step 11 — Response assembly.** `OrderService.toResponse` maps the entity into `OrderResponseDto` (never the raw entity), the controller wraps it in `ApiResponse.ok(...)`, and Spring's Jackson-based `HttpMessageConverter` serializes it to JSON.

**Step 12 — Response travels back up through the filter chain.** `SecurityContextHolderFilter`, on the way out, **wipes the `SecurityContext` clean** — the next request, even from the same client on the same TCP connection (HTTP/1.1 keep-alive), starts again from a completely empty context at Step 4. Nothing about "who you are" persists on the server between requests; it's rebuilt from the token, every single time.

**What happens instead if the token were tampered with or expired**: Step 5's `isTokenValid` returns `false`, `SecurityContext` never gets an authentication set, Step 7's `AuthorizationFilter` sees an unauthenticated request hitting a protected `anyRequest().authenticated()` rule, throws, and `ExceptionTranslationFilter` converts that into a `401 Unauthorized` — the controller method, and everything below it, never runs at all.

---

## SECTION 9 — CONCEPTS TO LEARN FROM THIS PROJECT

### What is REST, and what makes a *good* REST API?

REST (Representational State Transfer) is a style where every piece of data is a **resource** identified by a URL, and you act on it using standard HTTP verbs — `GET` (read), `POST` (create), `PUT` (replace), `PATCH` (partial update), `DELETE` (remove). Bakeaura is mostly clean REST: `/api/products/{id}` is "the product resource," `GET` reads it, `PUT` replaces it, `DELETE` removes it. A few deliberate, defensible deviations: `PATCH /api/cart/items/{productId}?quantity=N` uses a query param instead of a body for a single scalar value (arguably fine for something this small); `POST /api/orders/{orderId}/cancel` is an *action* disguised as a sub-resource creation (a common, pragmatic REST compromise — "cancel" isn't naturally a noun). What makes Bakeaura's API specifically *good*: a single consistent response envelope (`ApiResponse<T>`) across every single endpoint, consistent HTTP status code usage (Section 9's 4xx/5xx section), and resource URLs that read like sentences (`/api/sellers/{sellerId}/reviews`).

### What is a database transaction, and what does `@Transactional` actually do?

A transaction is a group of database operations that either **all** succeed together or **all** roll back together — there's no in-between state. The classic analogy: transferring money between two bank accounts must debit one and credit the other *atomically*; if the program crashes after the debit but before the credit, the money cannot be allowed to simply vanish. `@Transactional` in Spring wraps the annotated method in exactly this guarantee: Spring creates a proxy around your `@Service` bean, and when you call an `@Transactional` method, the proxy starts a database transaction *before* your code runs, and either commits it (if your method returns normally) or rolls it back (if your method throws any unchecked exception) *after* your code finishes. Concretely, in `OrderService.createOrder` (annotated `@Transactional`): if building the `Order` and its `OrderItem`s succeeds but then `OrderCreatedEvent`'s synchronous listener (`PaymentService.handleOrderCreated`, calling Razorpay) throws, **the entire order — and every item in it — is rolled back as if it never happened**, not left half-created in the database. "At the bytecode level": Spring doesn't modify your actual class file; it generates a runtime **proxy** class (via CGLIB, since these are concrete classes, not interfaces) that wraps every call to your real method with transaction-begin/commit/rollback logic — which is also *why* `@Transactional` famously doesn't work when you call another method on `this` from inside the same class (`this.someTransactionalMethod()`) — that call bypasses the proxy entirely and goes straight to the real object.

### What is the N+1 query problem, where can it happen in Bakeaura, and how is it prevented?

The N+1 problem: you fetch 1 list of N parent rows, then — because a related field is lazily loaded — your code triggers 1 *additional* query *per row* to fetch each one's related data, for a total of N+1 queries instead of 1 or 2. Concretely in Bakeaura: if you fetched an `Order` by ID using the default `orderRepository.findById(id)` and then called `order.getItems()` followed by `item.getProduct().getName()` for each item, Hibernate would lazily issue: 1 query for the order, 1 query for `items` (lazy collection), and 1 *more* query *per item* to fetch that item's `product` — for an order with 5 items, that's 7 queries where 1 would do. **Bakeaura's actual fix**: `OrderRepository.findByIdWithItems` uses `@Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id")` — the `JOIN FETCH` keyword tells Hibernate "eagerly load these related collections *in the same single SQL query*, via a SQL `JOIN`," collapsing what would have been N+1 round trips into exactly one. `OrderService` consistently calls this method (not the plain `findById`) anywhere it needs an order's items.

### Entity vs. DTO vs. Response — and why mixing them is dangerous

An **Entity** (`@Entity` classes like `User`, `Order`, `Product`) is a direct mirror of a database table — it can carry sensitive internal fields (`User.password`, `User.emailVerificationToken`), lazy-loading proxies that throw if accessed outside a transaction, and bidirectional relationships that can cause infinite-loop JSON serialization. A **DTO** (Data Transfer Object — `UserDto`, `ProductDto`, `OrderResponseDto`) is a plain, flat object built *specifically* to cross a boundary (HTTP response, or sometimes request), carrying only what that boundary needs. Bakeaura is disciplined about this almost everywhere: `UserService.toDto` builds a `UserDto` that simply never includes `password`. **Why mixing them is dangerous, concretely**: if a controller ever returned a raw `User` entity directly instead of a `UserDto`, Jackson would serialize *every* field, including the BCrypt password hash, straight into the HTTP response body — a real, severe security leak, not a theoretical one. It also couples your API's JSON shape directly to your database schema — renaming a database column would silently break every API consumer, instead of being an internal refactor invisible to the frontend.

### Dependency Injection — what it is, why constructor injection, what happens at startup

Dependency Injection (DI) means a class doesn't create its own dependencies (`new ProductService()`) — instead, something *outside* the class hands it fully-built dependencies it needs. Spring's DI container is that "something outside." Bakeaura uses **constructor injection exclusively**, via Lombok's `@RequiredArgsConstructor` (a `final` field on every dependency, and Lombok generates the constructor for you). Why constructor injection over field injection (`@Autowired` directly on a field) or setter injection: (1) a `final` field *must* be set before the object is usable, so it's **impossible** to construct, say, `OrderService` in a half-wired, partially-broken state — the compiler enforces it; (2) it makes unit testing trivial without Spring at all — every single test in `backend/src/test` constructs a service with `new OrderService(mock1, mock2, ...)` directly, no Spring context needed; (3) circular dependencies between beans (A needs B, B needs A) fail loudly at *startup* with constructor injection, instead of silently working via lazy-proxy tricks the way field injection sometimes allows — failing fast at boot is exactly what you want. **What happens at startup**: Spring scans every class for `@Service`/`@Repository`/`@Controller`/`@Component`, builds a dependency graph, and instantiates beans in dependency order (anything with zero unmet dependencies first), threading the right already-built instances into each constructor as it goes — if any class needs a bean that doesn't exist or that graph has a cycle, the app **refuses to start** and tells you exactly which bean is the problem, right there in the startup logs.

### Idempotency — why it matters for Razorpay webhooks, and how Bakeaura handles it

An operation is idempotent if doing it once produces the same end state as doing it five times. Webhooks are a classic place this matters: Razorpay's own infrastructure can, and sometimes does, deliver the *same* webhook event more than once (network retries on their side if your server's ack is slow or lost) — and separately, in Bakeaura specifically, **both** the frontend's `/api/payments/verify` call *and* Razorpay's independent `/api/payments/webhook` call can race to mark the *same* payment captured. `PaymentService.handlePaymentCaptured` and `verifyPayment` both guard with `if (payment.getStatus() != PaymentStatus.CAPTURED)` before doing anything — meaning whichever one arrives first does the real work (mark captured, reduce stock, confirm order, notify), and the second arrival is a safe, silent no-op, logged but otherwise harmless. **Without this check**, a duplicate webhook delivery could decrement product stock *twice* for one actual sale — a real inventory-corruption bug, not a cosmetic one.

### Eventual consistency — where Bakeaura accepts staleness, and where it doesn't

"Eventual consistency" means it's acceptable for different parts of the system to briefly disagree about the current state, as long as they converge to agreement *eventually*. Bakeaura **explicitly accepts** staleness in: the Redis-backed `@Cacheable` product/category/seller-profile reads (up to 10 minutes stale, by design — Section 6 #5); `SellerProfile.totalRatings`/`averageRating` columns, which are never actually kept in sync by any write path (Section 5) — arguably an *unintentional* staleness, but the live `ReviewService.getSummary` computation papers over it by not relying on those stale columns at all. Bakeaura **explicitly refuses** staleness in: stock quantity at the exact moment of payment capture (`@Version` optimistic locking, Section 6 #7 — this must be *strongly* consistent, never eventually so, because overselling is a real-money problem) and the wallet balance (always derived live from the full transaction history, never cached — Section 6 #6).

### Synchronous vs. asynchronous — thread pools, `@Async`, event listeners

Synchronous: the caller waits for the callee to finish before continuing. Asynchronous: the caller hands off work and continues immediately, without waiting. Bakeaura mixes both, deliberately: `OrderService.createOrder` publishing `OrderCreatedEvent` is **synchronous** (every listener runs on the same thread, in the same transaction, before `createOrder` returns — Section 6 #2's important nuance) — this is "asynchronous-*looking*, decoupling-wise" but synchronous-*executing*, a subtlety worth being precise about in an interview. `EmailService`/`SmsService`'s send methods are **genuinely asynchronous** (`@Async`, backed by a real separate thread pool — `core-size: 4, max-size: 8, queue-capacity: 100` in `application.yml`) — these *do* return control to the caller immediately while the actual work happens on a different thread later. `ReelService.processVideoUpload` is the clearest example of "fire off slow work asynchronously, then push the *eventual* result back to the client over WebSocket once it's actually done" — exactly the right pattern for "this will take a while and the user shouldn't have to sit on a spinning HTTP request waiting for it."

### Schema migration — why `ddl-auto: update` is fine in dev but dangerous in production, and what Flyway/Liquibase are

`hibernate.ddl-auto: update` tells Hibernate: "compare my `@Entity` classes to the actual database schema on startup, and automatically `ALTER TABLE`/`CREATE TABLE` whatever's missing to make them match." This is **convenient in development** — you change a field, restart the app, the column just appears, no manual SQL ever written. It is **genuinely dangerous in production** for several concrete reasons: Hibernate's `update` mode will *add* columns and tables, but it will **never drop or rename** anything (it can't safely infer "you renamed this field" vs. "you deleted this field and added an unrelated new one" — so renaming a Java field, in production, would leave the *old* column sitting there forever while creating a *new* one, silently). It also runs schema changes with zero review, zero rollback plan, and zero coordination with concurrently-running instances of the same app (in a multi-instance deployment, two instances racing to `ALTER TABLE` the same column on startup is a real, ugly failure mode). **Flyway** and **Liquibase** are the production-grade alternative: you write explicit, versioned, ordered migration scripts (Flyway: plain numbered `.sql` files; Liquibase: XML/YAML/SQL changesets) that get applied once, in order, and tracked in a metadata table — so the *exact* sequence of schema changes is reviewable, repeatable, and rollback-able, the same discipline you'd want for application code itself, applied to the database. Bakeaura currently has **neither** Flyway nor Liquibase configured — a completely reasonable simplification for a learning project, and exactly the kind of gap you'd flag and fix before any real production deployment.

### Race conditions — what they are, and how `Product.@Version` prevents overselling

A race condition happens when the correctness of a result depends on the precise timing of two (or more) things happening "at the same time," and that timing isn't guaranteed. The canonical Bakeaura example, walked through in full in Section 6 #7: two customers both reading `stockQuantity = 1`, both passing the stock check, both then writing `stockQuantity = 0` — selling the same single unit twice. `@Version` turns this into a *detectable*, *rejectable* event instead of a silent corruption, by making the second concurrent write fail outright rather than succeed incorrectly.

### Connection pooling — what HikariCP does, and why a new connection per request would be disastrous

Opening a brand-new database connection involves a real TCP handshake, authentication, and session setup on the Postgres side — genuinely expensive (often tens of milliseconds), and something you absolutely do not want to pay **on every single HTTP request**. HikariCP (Spring Boot's default connection pool, auto-configured the moment `spring-boot-starter-data-jpa` + a JDBC driver are on the classpath — no extra config needed in this project) opens a fixed, modest number of connections **once**, keeps them open, and *lends* one out to whichever thread needs to talk to the database right now, returning it to the pool the instant that thread is done — typically sub-millisecond to acquire from the pool versus tens of milliseconds to open fresh. **If every request opened its own fresh connection instead**: under any real concurrent load, Postgres's own hard connection limit (a finite number the database server itself enforces) would be exhausted almost immediately, and the handshake overhead alone would dominate every request's total latency, long before your actual business logic even ran.

### The difference between 4xx and 5xx errors

**4xx** means "the client did something the server can reasonably reject" — the request itself was the problem: bad input (`400`, `GlobalExceptionHandler`'s `BAD_REQUEST`/`VALIDATION_ERROR`), no valid credentials (`401`), valid credentials but insufficient permission (`403`), resource doesn't exist (`404`), too many requests (`429`, from `RateLimitFilter`). **5xx** means "the *server* failed to do its job properly" — the client did nothing wrong; something broke on Bakeaura's side (an unexpected `NullPointerException`, a downed database, an unhandled exception that falls through to `GlobalExceptionHandler`'s catch-all `Exception`→500). Getting this distinction right matters because client code (and humans debugging) treat the two completely differently: a 4xx means "fix your request and try again"; a 5xx means "this isn't something you can fix by changing your request — something is actually broken on the server, and retrying the identical request might or might not help." The three currently-broken `@AuthenticationPrincipal UserDetails` endpoints (Section 11) are a perfect real example of "this *should* be impossible to reach as a 500" — they're not bad client input, they're a genuine server bug, correctly surfacing as 500 once you trace why.

### What CORS is, and why the browser enforces it but Postman doesn't

CORS (Cross-Origin Resource Sharing) is a **browser** security mechanism — when JavaScript running on `http://localhost:5173` (the React dev server) tries to call `http://localhost:8080` (the API — a different port, hence a different "origin"), the browser itself, not the server, blocks the response from being readable by that JavaScript *unless* the server's response explicitly says "this origin is allowed" via `Access-Control-Allow-Origin` headers. This exists specifically to stop a malicious website from silently using *your* logged-in session/cookies against some *other* site's API on your behalf, in your browser, without your knowledge. `CorsConfig` is what makes Bakeaura's API respond with the right headers for the configured allowed origins. **Postman, cURL, and server-to-server calls never enforce CORS at all** — CORS is purely a restriction the *browser* imposes on *JavaScript running inside it*; a command-line tool or another backend service is not a browser and has no such restriction, which is exactly why "it works in Postman but not in the browser console" is such a common (and correctly diagnosed by understanding this) beginner confusion.

### What is SQL injection, and why JPA/Hibernate prevents it

SQL injection is when untrusted user input gets concatenated directly into a SQL string, letting an attacker smuggle in their own SQL logic (classic example: a login form vulnerable to entering `' OR '1'='1` as a password to bypass the check entirely). Spring Data JPA's derived query methods (`findByEmail(email)`) and `@Query` annotations with named/positional parameters (`:influencerId`, `?1`) **always** use parameterized queries under the hood — the actual SQL sent to Postgres has a `?` placeholder, and the user's value is sent *separately*, as data, never spliced into the SQL text itself — so even a maximally hostile email string like `"x' OR '1'='1"` is just treated as a literal string to search for, not as SQL syntax. **The one place this app does dynamic query *construction*** is `ProductService.filterProducts`'s JPA `Specification` — but it builds a typed `Predicate` tree via the Criteria API (`cb.equal(root.get("category").get("id"), categoryId)`), never raw string concatenation, so it inherits the same protection.

### What is XSS, and why it matters for a social platform like Bakeaura

Cross-Site Scripting (XSS) is when untrusted user-supplied content gets rendered as *executable* HTML/JavaScript in someone else's browser — e.g. if a seller's `shopBio` or a reel's `caption` contained `<script>steal(document.cookie)</script>` and the frontend rendered it unescaped, that script would actually *run* in the browser of every customer who views that storefront. Bakeaura has several genuinely user-generated free-text fields that flow straight to other users' screens — `shopBio`, `caption`, review `comment`, custom-order `designBrief`, role-application `message` — none of which are sanitized or HTML-escaped on the backend. The actual protection in this stack comes from the **frontend**, specifically React: React escapes string content by default when rendering it inside JSX (`{caption}` renders as literal text, not parsed HTML) — you'd have to deliberately opt out via `dangerouslySetInnerHTML` to reintroduce the vulnerability, which nothing in this frontend currently does. Worth being precise here in an interview: *"the backend doesn't sanitize this content itself, but the chosen frontend framework's default rendering behaviour happens to provide the protection — which is a real but fragile line of defence; a backend-side sanitization or escaping layer would be the more robust fix, in case this API is ever consumed by a different, less-careful frontend."*

---

## SECTION 10 — FRONTEND INTEGRATION GUIDE

The repo *does* have a working React frontend already (`frontend/src/`, Vite + React Router v6 + Zustand + Axios + react-hook-form/zod + react-hot-toast + `@stomp/stompjs`/`sockjs-client` for WebSocket) — 53 source files, 19 of them small `api/*.js` modules that each wrap one backend feature. (This corrects an earlier assumption in this project's own working notes that the frontend "hadn't been started" — it has, and it's reasonably complete, modulo the bugs noted below.)

### The shared contract every API call relies on

Every backend response is `ApiResponse<T>` (`{ success, message, data, errorCode?, timestamp? }`). `frontend/src/api/axios.js` centralises unwrapping this: `const data = (response) => response.data?.data ?? response.data`, and every `api/*.js` module's calls end in `.then(data)` — so feature code never has to think about the envelope at all, it just gets the inner payload directly.

### Auth header format and token refresh

`axios.js`'s request interceptor attaches `Authorization: Bearer <accessToken>` (read live from the Zustand store, `useAuthStore.getState().accessToken`) to every outgoing request. The response interceptor specifically watches for a `401` that **isn't** itself a request to `/auth/refresh` and **hasn't already been retried** (`originalRequest._retry`), then: pulls the stored `refreshToken`, calls `POST /auth/refresh`, and on success stores the new token pair and **retries the original failed request transparently** — the calling code never even sees the 401, it just gets its data, slightly delayed. Concurrent 401s while a refresh is already in flight are queued (`refreshQueue`) rather than firing N parallel refresh calls, which would otherwise race against the backend's refresh-token-rotation logic (Section 6 #1's "refresh rotates" detail) — if two refresh calls both consumed the *same* old refresh token, only one could possibly succeed depending on timing, and a real implementation has to defend against that exact race, which this one does correctly via queuing.

### What the frontend stores vs. what it must fetch separately

`useAuthStore` (Zustand + `persist` to localStorage under the key `bakeaura-auth`) holds exactly `accessToken, refreshToken, email, role, isAuthenticated` — **no numeric `userId` and no `name`**, because `AuthResponse` genuinely doesn't include them (Section 5). Any page that needs the current user's ID (e.g. to fetch "my own seller products") must separately call `GET /users/me` first.

### Loading states and error handling, as actually implemented

Pages generally call their API on mount (`useEffect`), with simple local `loading`/`error` state — e.g. `ReelFeedPage` shows a literal "Loading reels..." string while fetching, and a friendly fallback message on failure rather than letting an unhandled rejection crash the page. Mutation flows (`CheckoutPage.addAddress`, `placeOrder`) use `react-hot-toast` for success/error feedback, reading the backend's own message via `error?.response?.data?.message` — relying directly on `GlobalExceptionHandler`'s consistent error shape (Section 5) to show the *actual* server-side validation message, not a generic "something went wrong" on the frontend side.

### Razorpay checkout, end to end

`CheckoutPage.placeOrder`: (1) `ordersApi.createFromCart(...)` → backend creates the `Order` and (via the `OrderCreatedEvent` → `PaymentService` chain) a pending Razorpay order; (2) dynamically injects Razorpay's `checkout.js` script if not already loaded; (3) `paymentsApi.config()` → gets `{ keyId, currency }`; (4) opens `new window.Razorpay({ key, currency, order_id: order.razorpayOrderId, amount: order.totalAmount * 100, ... })` — note the `* 100`, converting rupees to paise client-side, mirroring `PaymentService.toPaise` server-side; (5) on Razorpay's success callback, calls `paymentsApi.verify({ razorpayOrderId, razorpayPaymentId, razorpaySignature })`, then navigates to `/orders/:id`.

### WebSocket subscriptions

`frontend/src/api/websocket.js`'s `createSocketClient()` wraps `@stomp/stompjs` + `sockjs-client` pointed at `<BACKEND_ORIGIN>/ws`. Pages that need live order tracking would subscribe to `/topic/order/:orderId` after connecting; the reel-upload flow (`ReelUploadPage`) builds its **own** separate STOMP client inline (not via the shared `websocket.js` helper) and subscribes to `/topic/reels/{sellerId}` specifically to flip from a "processing" spinner to "done"/"error" the moment the async Cloudinary upload finishes.

### Known frontend ↔ backend mismatches (verified, not theoretical)

1. **`ReelUploadPage`/`ReelFeedPage` double-prefix their URLs.** Every other `api/*.js` module correctly calls e.g. `api.get('/products')`, relying on `axios.js`'s `baseURL` already including `/api`. But `ReelUploadPage.handleUpload` calls `api.post("/api/reels/upload", ...)` and `ReelFeedPage.fetchReels` calls `api.get("/api/reels/feed?page=0&size=20")` — both hardcode an *extra* `/api/` prefix, producing a real request to `http://localhost:8080/api/api/reels/...`, which 404s against the actual `/api/reels/...` mapping. **Reel upload and the reel feed page are both currently non-functional from the UI as written**, independent of the backend's own `@AuthenticationPrincipal` bug on the upload endpoint (Section 11) — two separate, stacked problems on the same feature.
2. **`ReelFeedPage` also misreads the response shape.** Even with the URL fixed, `ReelController.getFeed` returns a raw `Page<ReelResponseDTO>` (Spring Data's pagination wrapper — `{ content: [...], totalPages, totalElements, ... }`), **not wrapped in `ApiResponse`** and **not a bare array**. `ReelFeedPage` does `setReels(response.data || [])`, expecting `response.data` to already be the array — it would actually receive the `Page` object, and `.map()`-ing over it in the JSX would fail, since a `Page` object isn't an array.
3. **`contentApi.feed(params)` sends query params the backend ignores.** `ContentController.getFeed()` takes no `@RequestParam`s at all — any `type`/`q`/`sellerId`/`page`/`size` the frontend sends are silently dropped server-side; the endpoint always returns the *entire* ranked feed, unfiltered and unpaginated.
4. **No backend endpoint serves a public influencer directory** at `GET /api/influencers`/`GET /api/influencers/{id}`, despite `frontend/src/api/influencers.js` calling exactly those paths and `SecurityConfig` permitting them publicly (Section 7's note) — calling this from the frontend today would 404.
5. **Seller self-service is unreachable from the UI**, because `SellerController.updateProfile`/`toggleOpen` both throw `NullPointerException` server-side (Section 11) — `MyProductsPage`'s broader "manage my shop" experience would need this fixed first.

---

## SECTION 11 — KNOWN ISSUES AND DEFERRED ITEMS

Every item below was verified by reading the actual source, not inferred — most include the exact reasoning that proves it, so you can re-verify it yourself in seconds.

### 🔴 Critical — `@AuthenticationPrincipal UserDetails` resolves to `null`, causing NPEs on real endpoints

**Files affected**: `seller/SellerController.java` (`updateProfile`, `toggleOpen`), `reel/ReelController.java` (`uploadReel`), `influencer/InfluencerCollaborationController.java` (`requestCollaboration`, `getIncomingRequests`, `getMyOutgoingRequests`, `respondToRequest`) — **7 endpoints across 3 controllers**.

**Why it happens**: `auth/JwtAuthFilter.java` sets the Spring Security principal as a plain `Long` (`new UsernamePasswordAuthenticationToken(userId, null, authorities)`). There is **no `UserDetailsService` anywhere in this codebase, and no class implements `UserDetails`** (verified via a project-wide search — zero matches for either). Spring's `@AuthenticationPrincipal` argument resolver checks whether `authentication.getPrincipal()` is assignable to the declared parameter type; a `Long` is not a `UserDetails`, so by default (`errorOnInvalidType = false`) it silently injects `null` instead of throwing. Every one of the seven methods above then calls `userDetails.getUsername()` (or, in `SellerController`'s case, additionally tries `Long.parseLong(userDetails.getUsername())`, compounding the issue) — a guaranteed `NullPointerException`, caught by `GlobalExceptionHandler`'s generic handler and returned to the client as an opaque `500 Something went wrong`.

**Why it was deferred**: every other controller in the app consistently uses `Authentication authentication` + `Long.parseLong(authentication.getName())` instead — these seven methods are the only places that pattern wasn't followed, almost certainly written at a different time or copied from boilerplate that assumed a more "textbook" Spring Security setup.

**Exact fix**: in each of the three files, replace `@AuthenticationPrincipal UserDetails userDetails` parameters with `Authentication authentication`, and replace every `Long.parseLong(userDetails.getUsername())` / `userDetails.getUsername()` usage with `Long.parseLong(authentication.getName())` — the same one-line change repeated seven times, matching the convention every other controller already follows.

### 🔴 Critical — Test suite is stale and will not compile against current source

**Files affected**: `AuthServiceTest.java`, `JwtAuthFilterTest.java`, `CartControllerTest.java`, `CartServiceTest.java`, `OrderControllerTest.java`, `OrderServiceTest.java`, `PaymentControllerTest.java`, `PaymentServiceTest.java` — 8 of the 12 test files.

**Why it happens**: at some point, the production code migrated its "who is the caller" convention from **email-as-principal** to **numeric-userId-as-principal** (Section 6 #1) — but the tests were never updated to match. Concrete, line-level evidence: `JwtAuthFilterTest` calls `jwtUtil.extractEmail("access-token")` — `JwtUtil` has no `extractEmail` method at all anymore (only `extractUserId`); this alone fails to compile. `OrderServiceTest` constructs `new OrderService(orderRepository, productRepository, userRepository, mapService, paymentService, orderTrackingService, cartService, notificationService)` — 8 arguments, including a raw `ProductRepository` and a `PaymentService` — but the real `OrderService` constructor takes **10** arguments in a different order (`orderRepository, productService, userRepository, mapService, cartService, notificationService, orderTrackingService, eventPublisher, emailService, smsService`), with no `PaymentService` dependency at all (that coupling was replaced by the event-driven pattern, Section 6 #2). `CartServiceTest` calls `cartService.addItem("customer@example.com", 1L, 2)` — the real method signature is `addItem(Long userId, Long productId, int quantity)`; passing a `String` where a `Long` is expected is a compile error, not a runtime one. The same category of mismatch (email string vs. `Long`, and/or wrong constructor arity) breaks all 8 files.

**Why it was deferred**: this is exactly the kind of debt that accumulates when a cross-cutting refactor (changing the JWT principal type) touches production code everywhere but the corresponding test updates get pushed to "later."

**Exact fix**: for each broken test, update mock setups and method calls to use `Long` user IDs matching the real method signatures, and fix constructor argument lists/order to match the real classes — essentially a mechanical "find every email string used as if it were a user ID, and the wrong-shaped constructor calls, and correct them to match current production code." `MapServiceTest`, `CategoryControllerTest`, `CategoryServiceTest`, and `BakeauraBackendApplicationTests` are unaffected and remain accurate.

### 🟠 High — Referral commissions are fully built but never wired up; influencers never actually get paid for referrals

**Files affected**: `referral/ReferralOrderService.java` (the `processReferral` method itself), `order/OrderService.java` (where it *should* be called, alongside `eventPublisher.publishEvent(new OrderCreatedEvent(...))`), `order/OrderCreatedEvent.java` (already carries the `referralCode` field, end to end, with nowhere to deliver it).

**Why it happens**: `OrderCreatedEvent` was clearly *designed* to carry a referral code to some listener — but no `@EventListener` anywhere subscribes to `OrderCreatedEvent` and calls `ReferralOrderService.processReferral`. A project-wide search for `processReferral`/`ReferralOrderService` confirms the only two matches are the class's own declaration and its method declaration — zero call sites.

**Exact fix**: add a new `@EventListener` method (most naturally inside `ReferralOrderService` itself, mirroring how `PaymentService`, `CartService`, and `NotificationService` each listen for the same event) — `@EventListener public void handleOrderCreated(OrderCreatedEvent event) { if (event.getReferralCode() != null) processReferral(event.getOrder().getId(), event.getReferralCode(), event.getOrder().getTotalAmount()); }`.

### 🟠 High — `SellerProfile.totalRatings`/`averageRating` columns exist but are never written

**Files affected**: `seller/SellerProfile.java` (the columns), `review/ReviewService.java` (the actual, *correct*, live-computed average that's used instead).

**Why it happens**: two parallel mechanisms for "what's this seller's rating" were built — stored columns on `SellerProfile`, and a live `AVG()` query in `ReviewRepository.averageRatingForSeller` — and only the second is actually used anywhere (`SellerService.toDto` reads `profile.getAverageRating()`/`profile.getTotalRatings()` directly from the never-updated columns, so in practice these always show `0.0`/`0` regardless of real reviews).

**Exact fix**: either delete the two unused columns entirely (since `ReviewService.getSummary` already does this correctly, on demand, with caching) and have `SellerService.toDto` call `reviewService.getSummary(seller.getId())` instead, or — if you want it denormalised for performance reasons — update both columns inside `ReviewService.createReview`/`deleteReview` every time a review changes.

### 🟡 Medium — Dead config and code

- **`config/JwtConfig.java`** is an entirely empty class — no fields, no methods, no annotations. Safe to delete.
- **`application.yml`'s `resilience4j.circuitbreaker.instances.gemini`** block is fully configured but nothing in the codebase calls any Gemini/Spring AI API — likely scaffolding for the unbuilt `CustomOrderRequest.generatedImageUrl` AI-image feature. Either build that feature, or remove the dead config.
- **`RateLimitFilter`'s `/api/v1/ai/**` branch** rate-limits a path no controller serves — dead code, harmless but misleading; remove it or build the AI endpoint it's anticipating.
- **`PaymentStatus.REFUNDED`** and **`PayoutStatus.PAID`** are declared enum values with **zero code paths that ever set them** — `PaymentService` never issues refunds, and `PayoutRequestService.approveRequest` only debits the wallet, never calls an actual payment-disbursement API and marks `PAID`. Both are intentional "future work" placeholders, not bugs, but worth knowing they're currently unreachable if you're asked to trace every possible state.

### 🟡 Medium — Generic exceptions bypass the clean error-handling contract

**Files affected**: `customorder/CustomOrderRequestService.java` (throws plain `IllegalStateException`/`IllegalArgumentException`), `payout/PayoutRequestService.java` (same).

**Why it matters**: `GlobalExceptionHandler` has no specific `@ExceptionHandler` for either exception type, so both fall through to the catch-all `Exception`→500 handler — meaning "you already have a pending custom order request with this seller" (a clear 400-level client mistake) currently comes back to the frontend as an opaque `500 Something went wrong`, hiding the actual helpful message and giving the wrong HTTP status semantics (Section 9's 4xx/5xx distinction).

**Exact fix**: change these two services to throw the app's own `BadRequestException`/`ResourceNotFoundException`/`AccessDeniedException` instead, matching every other service in the codebase.

### 🟡 Medium — No automated stock check for `INSTANT` orders against `minAdvanceDays`

`OrderService.createOrder`'s `minAdvanceDays` validation (Section 6 #10) only runs when `orderType == SCHEDULED`. Whether an `INSTANT` order for a pre-order-only product should be rejected outright (since by definition it can't satisfy any advance-notice requirement) is unresolved in the current code — worth a deliberate decision either way, rather than leaving it implicit.

### 🟡 Medium — WebSocket topics have no per-user/per-order authorization

`/ws/**` is `permitAll()` in `SecurityConfig`, and neither `OrderTrackingController`/`OrderTrackingService` nor `NotificationService`'s broadcast checks whether the subscribing client is actually entitled to see updates for that specific `orderId`/`userId` — anyone who can guess or enumerate an order ID or user ID can subscribe to its topic and observe its live updates. Low real-world severity today (IDs are sequential but not otherwise secret, and no sensitive financial data flows over these specific messages), but a genuine authorization gap worth closing — e.g. by validating, inside `OrderTrackingController.joinOrderRoom`, that the connected session's authenticated user is actually the order's customer/seller/an admin before allowing the subscription.

### 🟢 Low — Rate limiting is in-memory and per-instance

`RateLimitFilter`'s `ConcurrentHashMap`-backed buckets reset on every restart and aren't shared across multiple app instances behind a load balancer — fine for a single-instance deployment (which this project's `docker-compose.yml` is), but would need a Redis-backed Bucket4j configuration before any horizontally-scaled production deployment.

### 🟢 Low — `getNearbySellers` is an unindexed in-memory scan

`SellerService.getNearbySellers` loads *every* active seller and filters in Java using Haversine distance per row — perfectly fine at the scale of hundreds of sellers, but would need a proper geospatial index (PostGIS, or geohash bucketing) to stay performant at large scale.

### 🟢 Low — Frontend reel pages are non-functional as written

Covered in full in Section 10: `ReelUploadPage`/`ReelFeedPage` double-prefix their request URLs with `/api/`, and `ReelFeedPage` doesn't account for the backend returning a Spring `Page` object rather than a bare array.

### 🟢 Low — Configuration secrets ship with insecure placeholder defaults

`application.yml`/`.env.example` default `jwt.secret` to `bakeaura_dev_secret_change_me`, the local Postgres password to `hello`, and Razorpay keys to obvious placeholders (`rzp_test_your_key`). Entirely appropriate for a checked-in example/dev config, but worth stating explicitly: **none of these defaults must ever reach a real deployment** — they exist purely so `docker compose up` works out of the box for local development.

---

## SECTION 12 — PLACEMENT INTERVIEW PREPARATION

### Modular monolith architecture

**Interview answer**: "Bakeaura is one deployable Spring Boot app, but internally organised into ~28 feature packages, each owning its own entity, controller, service, and repository, with a convention that cross-feature calls go through another package's Service, not its Repository directly. This gets most of microservices' organisational clarity without the operational cost of distributed transactions and network calls between your own features."

**Cross-questions**:
1. *"Why not just use microservices from the start?"* — At this scale (one team, one product), microservices would add distributed-transaction complexity, network failure handling, and service-discovery overhead for a problem one Postgres instance and one JVM solve trivially; the modular monolith keeps the option to extract a service later along the package seams already drawn.
2. *"Is the boundary rule followed everywhere?"* — No, and I can name the exception precisely: almost every service injects `UserRepository` directly rather than going through `UserService`, because `User` is treated as a shared-kernel entity nearly every feature needs — a deliberate, defensible trade-off, not an oversight.
3. *"How would you actually split this into microservices later?"* — Cut along the existing package boundaries — `payment/`, `order/`, `notification/` are already the loosest-coupled (communicating via `ApplicationEvent`s, not direct calls), so they'd convert most naturally to async message-queue-based services first.

**System design angle**: this architecture scales *team* size and *codebase clarity* well before it needs to scale *infrastructure* — the right call for an MVP/early-stage product, and the same reasoning real companies use before "premature microservices."

**Common mistakes candidates make**: claiming "modular monolith" when the codebase actually has no enforced boundaries at all (just folders); not being able to name a concrete boundary violation when pushed (every real codebase has some — naming yours honestly is a stronger signal than claiming perfection).

### JWT design — numeric ID as subject, access/refresh split

**Interview answer**: "Tokens carry the user's numeric primary key as the subject, not their email, so every authenticated request can go straight to a primary-key lookup, and the token stays valid even if the user later changes their email through the two-step flow. Access tokens are short-lived (15 min) for blast-radius limitation if leaked; refresh tokens are long-lived (7 days), stored server-side in Redis so they can be revoked on logout, and rotate on every use."

**Cross-questions**:
1. *"What stops someone from using a stolen refresh token forever?"* — Rotation: every refresh issues a brand-new refresh token and immediately invalidates the old one in Redis, so a stolen-and-used token becomes worthless to the original holder the next time *they* try to use it — whoever uses it first "wins" the session, which at least bounds the damage and makes reuse detectable in principle (though this codebase doesn't currently alert on detecting reuse, which would be the next hardening step).
2. *"Why not put the role in a database lookup instead of the token?"* — Putting it in the token avoids a database round-trip on every single authenticated request just to know the caller's role; the trade-off is that a role change (e.g. admin promotes someone) doesn't take effect until their current access token expires (≤15 minutes here) — an accepted, bounded staleness window.
3. *"What's the actual security risk if `jwt.secret` leaks?"* — Total compromise: anyone with the secret can forge a valid, signed token for any user ID and any role, including ADMIN — there's no way to distinguish a forged token from a real one without the secret, which is *the* reason it must be long, random, and never committed to source control in a real deployment.

**System design angle**: stateless JWT auth is what lets this API scale horizontally without a shared session store — any instance can validate any token using only the shared secret, no inter-instance session replication needed.

**Common mistakes**: confusing encoding with encryption (a JWT payload is *readable* by anyone, just tamper-evident, not hidden — Section 3/`JWT.md` covers this distinction in detail); forgetting that `@PreAuthorize` checks the caller's *own* role but never automatically checks object ownership (you still need explicit checks like `order.getCustomer().getId().equals(callerId)`).

### Idempotent payment handling

**Interview answer**: "Two independent paths can mark the same Razorpay payment captured — the frontend's verify call and Razorpay's own webhook — so both check `payment.getStatus() != CAPTURED` before doing any further work. Whichever arrives first does the real work (reduce stock, confirm order, notify); the second is a safe no-op. This also means a duplicate webhook delivery — which Razorpay's own infrastructure can genuinely send — never double-deducts stock."

**Cross-questions**:
1. *"Why might Razorpay send the same webhook twice?"* — Network unreliability on their side: if their server doesn't receive your acknowledgement fast enough (or your server's ack is lost in transit), their retry logic resends — webhook idempotency is industry-standard practice precisely because "exactly once" delivery isn't something HTTP itself guarantees.
2. *"What's the actual database mechanism that makes this safe under concurrency, not just 'on paper'?"* — Honestly, in this specific implementation, the safety relies on the application-level status check inside a `@Transactional` method, not a database-level constraint — a genuinely sharper implementation would add a unique constraint or use `SELECT ... FOR UPDATE` on the payment row to make the check-then-act sequence atomic even under true concurrent webhook delivery; today there's a narrow theoretical race if both paths read `PENDING` in the same instant before either writes.
3. *"How do you verify the webhook is actually from Razorpay, not an attacker?"* — HMAC-SHA256 signature verification using a webhook-specific secret known only to Bakeaura and Razorpay, compared with `MessageDigest.isEqual` (constant-time, to resist timing attacks) rather than a naive string comparison.

**System design angle**: idempotency is a prerequisite for *any* reliable distributed system communicating over an unreliable network (which all of them are) — this is the same principle behind idempotency keys in Stripe's API, exactly-once processing semantics in message queues, etc.

**Common mistakes**: assuming "the webhook will only ever fire once" (it won't, by design of how webhook providers handle delivery failures); checking idempotency *after* doing the side effect instead of before.

### Circuit breaker pattern

**Interview answer**: "Calls to Razorpay and Cloudinary are wrapped in Resilience4j circuit breakers with fallback methods. If failures exceed a threshold within a sliding window, the breaker trips OPEN and fails fast for a cooldown period instead of letting every new request hang waiting on a clearly-broken dependency — protecting our own thread pool from exhaustion."

**Cross-questions**:
1. *"Walk me through CLOSED → OPEN → HALF-OPEN."* — (Full walkthrough in Section 6 #3 — be ready to recite it precisely, including what "sliding window" and "half-open test calls" mean concretely.)
2. *"Why does thread-pool exhaustion matter so much?"* — Without a breaker, every request hitting a slow dependency occupies a thread until its own timeout; under load, this can consume every available thread in the pool, so even requests that have *nothing to do with* the failing dependency (e.g. browsing products while Razorpay is down) start failing too — the classic cascading-failure pattern.
3. *"What would you monitor to know if your thresholds are well-tuned?"* — The breaker's own state-transition metrics (Resilience4j exposes these, and Spring Boot Actuator can surface them) — frequent flapping between CLOSED/OPEN suggests the threshold is too sensitive for normal traffic variance; never tripping during a real known outage suggests it's too lenient.

**System design angle**: this is the same defensive pattern behind Netflix's Hystrix (Resilience4j's spiritual predecessor) — essential for any system with hard external dependencies it doesn't control the uptime of.

**Common mistakes**: forgetting the fallback method's signature must exactly match the original plus a trailing `Throwable` (Resilience4j can't find a mismatched fallback and the original exception just propagates); conflating circuit breakers with retries — they solve different problems (a circuit breaker stops you from retrying a clearly-broken dependency; a retry policy is what you'd add *on top* for transient blips, and the two are often combined, not substitutes for each other).

### Optimistic locking

**Interview answer**: "`Product` has a `@Version` field. Hibernate includes it in every `UPDATE`'s `WHERE` clause and increments it on success, so if two requests load the same product and both try to save after each other read the same version, the second `UPDATE` matches zero rows and throws — preventing two customers from both successfully buying the last unit of stock."

**Cross-questions**:
1. *"Why optimistic over pessimistic locking here?"* — Pessimistic locking (`SELECT FOR UPDATE`) pays a blocking cost on *every* access, contended or not; optimistic locking costs nothing in the common, uncontended case and only costs a retry in the rare case of real contention — and most products aren't being bought by two people in the same millisecond.
2. *"What happens to the user when this exception is thrown — is it handled gracefully today?"* — Honestly, no — I checked, and `OrderService`/`CartService` don't currently catch `OptimisticLockException` specifically, so it would surface through `GlobalExceptionHandler`'s generic 500 handler rather than a friendly "someone just bought the last one, please retry" message; that's a real gap I'd fix before shipping this to real traffic.
3. *"Where else in the system would optimistic locking *not* be appropriate?"* — Anywhere contention is the *expected common case*, not the rare exception — e.g. a single shared counter incremented by every request would thrash under optimistic locking (constant retries); that's where you'd reach for an atomic database operation or pessimistic locking instead.

**System design angle**: this is the textbook solution to the "lost update" race condition class, directly relevant to any e-commerce inventory system, and a near-guaranteed system-design interview topic ("how do you prevent overselling").

**Common mistakes**: confusing optimistic locking with database transaction isolation levels (related but distinct concepts — isolation levels are about what one transaction can *see* of another's uncommitted/committed changes; optimistic locking is an application-level check on top); not knowing that the version check happens at `UPDATE` time, not at read time — reading stale data is still possible, it's *acting* on stale data without detection that's prevented.

### Wallet as an append-only ledger

**Interview answer**: "The wallet has no stored balance column at all — `WalletService.getBalance` always computes it live as `SUM(credits) - SUM(debits)` over an immutable transaction history. This is the same principle real accounting and banking systems use: a derived value from an append-only log can't drift from the truth, because it *is* the truth, recomputed."

**Cross-questions**:
1. *"Isn't recomputing the sum every time slow at scale?"* — At very high transaction volumes per account, yes — the standard production answer is a periodic, cached "checkpoint balance" (e.g. "as of last Tuesday, balance was X") plus only summing transactions *since* that checkpoint, getting the best of both: bounded query cost and the same drift-proof guarantee, since the checkpoint itself is just another derived snapshot, never a hand-edited source of truth.
2. *"What stops someone from manually editing the database to fake a credit?"* — Nothing intrinsic to this design stops a privileged database-level edit — the pattern protects against *application bugs* causing drift, not against malicious direct database access, which needs its own defences (audit logging, restricted DB credentials, etc.) — an honest, important distinction to draw if pushed on this.
3. *"Why is this specifically safer than `balance += amount`?"* — Because `+=` requires perfect atomicity between updating the balance and recording *why* it changed — any crash, bug, or race between those two writes (if they're not in the same transaction) leaves you with a balance that doesn't match its own history, and no way to even detect that it happened, let alone reconstruct the correct value.

**System design angle**: this is literally double-entry bookkeeping (and the same conceptual ancestor as event-sourcing/CQRS architectures) — a deep, correct system-design instinct that goes well beyond "just store a number."

**Common mistakes**: candidates who've never seen this pattern often answer "use a database transaction to keep the balance column in sync" — which solves the atomicity problem but *not* the "how do I know this number has never silently drifted, ever, in its whole history" problem that the ledger approach solves structurally.

### Caching strategy and invalidation

**Interview answer**: "Redis-backed `@Cacheable`/`@CacheEvict` on read-heavy, write-rare data — categories, products, seller profiles, review summaries — with a 10-minute default TTL. Invalidation is deliberately blunt: every write evicts the *entire* cache region rather than trying to surgically target just the changed key, which is simpler to get right and acceptable because writes are rare relative to reads here."

**Cross-questions**:
1. *"What's the actual risk of stale cached data here, concretely?"* — A customer could see a product as available/in-stock for up to 10 minutes after a seller marked it unavailable — annoying but not dangerous, since the *actual* stock/availability check happens fresh, server-side, at add-to-cart and at order-creation time regardless of what the cached listing showed; the cache only affects what's *displayed*, never what's *enforced*.
2. *"Why blunt eviction instead of fine-grained?"* — A single product update could affect "all products," "products in category X," "products by seller Y," and multiple search-keyword caches simultaneously — correctly enumerating and evicting every affected key is genuinely hard to get exactly right, and getting it *wrong* (missing one) is worse than the minor cost of wiping the whole region.
3. *"How would this break under a 'famous person tweets about this seller and traffic spikes 100x' scenario?"* — The cache would actually help here — once warm, repeated reads hit Redis, not Postgres, so a traffic spike on already-cached, popular data is exactly what this caching layer is *for*; the bigger risk would be a "cache stampede" the moment that hot key's TTL expires and many concurrent requests simultaneously miss and hit the database at once — a known limitation of simple TTL caching that this implementation doesn't specifically guard against (e.g. via a lock or "probabilistic early refresh").

**System design angle**: this is the read-through cache pattern, foundational to almost every high-traffic web system; knowing *when* staleness is acceptable (and proving you understand exactly what's at risk) matters more than reciting "use Redis."

**Common mistakes**: caching data that must be strongly consistent (this app correctly never caches stock quantity or wallet balance); forgetting `@CacheEvict` on a new write path as the codebase grows (a classic, hard-to-catch-in-testing bug).

### N+1 prevention with `JOIN FETCH`

**Interview answer**: "Fetching an order's items and each item's product the naive, lazy-loaded way costs 1+N+N queries for N items. `OrderRepository.findByIdWithItems` uses an explicit JPQL `JOIN FETCH` to eagerly pull the order, its items, and each item's product in exactly one SQL query, and `OrderService` consistently uses this method anywhere it needs an order's items."

**Cross-questions**:
1. *"How would you detect this problem in a codebase that didn't already know about it?"* — Enable `hibernate.show-sql` (already on here) or use a tool like Hibernate's statistics/`p6spy`/Datadog's query tracing in a staging environment under realistic data volumes, and watch for "one query, then a suspicious burst of nearly-identical queries right after" — the textbook N+1 signature.
2. *"Is `JOIN FETCH` always the right fix?"* — No — for a *list* of many parent entities each needing a *large* eagerly-fetched collection, a single giant `JOIN FETCH` can return a cartesian-product-sized result set that's actually slower to transfer than N+1 smaller queries would have been; `@BatchSize` or a second batched query (`WHERE parent_id IN (...)`) are sometimes the better fix for that specific shape of problem.
3. *"What's the difference between `FetchType.LAZY` on the mapping itself and `JOIN FETCH` in a query?"* — `FetchType.LAZY` is the *default* behaviour declared once on the entity mapping (don't load until accessed); `JOIN FETCH` is a *per-query* override that says "for this specific query, eagerly load this association anyway" — you keep the safe lazy default everywhere else and opt into eager loading only where you specifically know you'll need it.

**System design angle**: N+1 is one of the most common real-world performance bugs in any ORM-based system, and the ability to both *spot* it and *choose the right fix for the specific access pattern* (not just always reaching for one tool) is a strong signal.

**Common mistakes**: "fixing" N+1 by switching every relevant `@ManyToOne`/`@OneToMany` to `FetchType.EAGER` globally on the entity — this just moves the problem to *every* query that touches that entity, even ones that never needed the related data at all.

### Event-driven decoupling with `ApplicationEvent`

**Interview answer**: "Creating an order publishes one `OrderCreatedEvent`; four separate services (payment, cart, notification, order-tracking) each independently listen and react, without `OrderService` knowing any of them exist. This is publish/subscribe inside a single JVM — Spring's default behaviour runs listeners synchronously, on the same thread and transaction as the publisher, which is an important nuance, not a minor detail."

**Cross-questions**:
1. *"If this runs synchronously, what's actually decoupled?"* — *Compile-time/dependency* coupling, not *runtime timing* coupling — `OrderService` has zero `import` of `PaymentService`/`CartService`/`NotificationService`, so you can add, remove, or change any listener without touching `OrderService` at all; but you're correct that a slow or failing listener still directly affects the publisher's own request latency and transaction outcome, which is the trade-off worth being explicit about.
2. *"How would you make a listener genuinely asynchronous if you needed to?"* — Add `@Async` to the listener method (with `@EnableAsync` already on, as it is here) or switch to `@TransactionalEventListener(phase = AFTER_COMMIT)` if you specifically want it to run only after the publishing transaction successfully commits, decoupling both the timing *and* the transactional fate.
3. *"What happens if one listener throws?"* — By default, that exception propagates back into the publisher's call stack — in `OrderService.createOrder`'s case, it would roll back the entire `@Transactional` order creation, including the order and all its items, even though the *order creation itself* logically succeeded before the listener ran — worth knowing this is a real consequence, not a hypothetical.

**System design angle**: this is the in-process equivalent of a message broker (Kafka/RabbitMQ) — the same decoupling philosophy, at a much smaller operational cost, appropriate exactly because everything still lives in one process/transaction; recognising *when* you'd graduate from this to a real message queue (multi-instance deployments, need for true async + retry + dead-letter handling) is a strong signal.

**Common mistakes**: assuming `@EventListener` is inherently asynchronous (it isn't, by default); not realizing a listener's exception can roll back the publisher's transaction.

### Token bucket rate limiting

**Interview answer**: "Auth and payment endpoints are protected by per-IP token buckets (Bucket4j) — each bucket holds a fixed number of tokens that refill continuously over a time window; exceeding the bucket returns 429 immediately, before the request ever reaches the controller."

**Cross-questions**:
1. *"Why token bucket over a fixed window counter?"* — Fixed windows have a boundary-burst problem — a client can send a full window's worth of requests right at the end of one window and another full window's worth right at the start of the next, doubling the effective rate for a brief moment; a continuously-refilling bucket smooths this out.
2. *"Does this protect against a distributed attack from many different IPs?"* — No — per-IP rate limiting specifically defends against one attacker hammering from one (or a few) source IPs; a genuinely distributed credential-stuffing attack from thousands of IPs needs a different defence layer entirely (CAPTCHA, anomaly detection, account lockout after N failed attempts regardless of source IP).
3. *"What's the actual weakness of this specific implementation?"* — It's in-memory and per-app-instance — in a horizontally-scaled, multi-instance deployment, each instance has its own independent bucket per IP, so the *effective* allowed rate scales up with the number of instances, defeating the intent; a Redis-backed shared bucket store fixes this.

**System design angle**: rate limiting is foundational to API abuse prevention and a near-universal system-design interview topic; being able to name the specific weakness of *this* implementation (rather than reciting the pattern generically) is what separates a strong answer.

**Common mistakes**: applying rate limits uniformly to all endpoints instead of specifically the abuse-sensitive ones (login, register, payment) — over-limiting harmless high-traffic reads (like `GET /products`) for no security benefit, just user friction.

### Snapshot/denormalization pattern (price-at-purchase, delivery-address-on-order)

**Interview answer**: "`OrderItem.priceAtPurchase` and `Order.deliveryAddress`/coordinates are copied values, not foreign keys to the live product/address — because an order is a historical receipt, and a receipt must never silently change just because the live data it was based on changed later."

**Cross-questions**:
1. *"Isn't this just data duplication — a normalization violation?"* — Yes, deliberately — strict normalization optimizes for "never store the same fact twice"; this case optimizes for "the historical fact, as it was at a specific moment, must be immutable and independent of the present" — those are different, sometimes competing goals, and recognising *when* to deliberately trade one for the other is the actual skill being tested.
2. *"Where else would you apply this same pattern?"* — Anywhere you're recording a *fact about a moment in time* that must survive later edits to its sources — invoice line items, audit logs, "terms accepted at signup," shipping labels — the unifying principle is "if this thing is a receipt/record, snapshot it; if it's a live reference, link it."
3. *"What's the failure mode if you got this backwards?"* — Storing a foreign key to the live address instead of a snapshot means editing your saved home address could silently rewrite the delivery address shown on a three-month-old, already-delivered order — a real, confusing data-integrity and customer-trust bug, not just a style preference.

**System design angle**: this distinction (mutable reference vs. immutable snapshot) is central to event-sourcing, audit-log design, and financial-record-keeping generally — a pattern interviewers specifically probe for in e-commerce/fintech-flavoured system design rounds.

**Common mistakes**: "fixing" perceived duplication by replacing a snapshot with a foreign key, not realizing it breaks the historical-accuracy guarantee the snapshot existed to provide.

### `@Async` and background processing

**Interview answer**: "Email/SMS sends and the Cloudinary video-upload pipeline run on a separate thread pool via `@Async`, so a slow third-party call never adds its latency to the HTTP response the user is actually waiting on. The reel-upload flow specifically returns immediately with a 'processing' status, then pushes the eventual result over WebSocket once the async work finishes — no polling needed."

**Cross-questions**:
1. *"What happens to an exception thrown inside an `@Async` method?"* — By default it never reaches the original caller (who's already moved on) — Spring just logs it via a default uncaught-exception handler; this codebase deliberately catches every exception *inside* `EmailService`/`SmsService`'s own methods rather than relying on that default behaviour, specifically so failures are always logged with full context regardless of Spring's async exception handling.
2. *"How is the thread pool itself configured, and what happens if it's exhausted?"* — `core-size: 4, max-size: 8, queue-capacity: 100` — up to 4 threads run concurrently with no queueing, growing to 8 under load, with up to 100 additional tasks queued beyond that before new submissions would be rejected (Spring's default `ThreadPoolTaskExecutor` rejection policy applies once the queue is also full) — worth knowing these aren't infinite, and a sudden burst of thousands of simultaneous notification sends could theoretically hit that ceiling.
3. *"Why is `@EnableAsync` necessary — what would happen without it?"* — Without it, Spring never creates the proxy that intercepts `@Async`-annotated method calls, so every such method would silently run **synchronously**, on the caller's own thread — a subtle, easy-to-miss configuration dependency, not an error you'd necessarily notice immediately (the app would still "work," just slower under load).

**System design angle**: this is the foundational technique behind any "accept the request fast, do the slow work later" architecture — directly scalable to a real message-queue-backed background-job system (Celery/Sidekiq/Spring's own `@Async` is the in-process baby version of that same idea).

**Common mistakes**: forgetting `@EnableAsync`; calling an `@Async` method on `this` from inside the same class (bypasses the Spring proxy entirely, runs synchronously — the exact same self-invocation pitfall as `@Transactional`, Section 9).

### Security request lifecycle (filters, `SecurityContextHolder`)

**Interview answer**: (Full walkthrough in Section 8 — be ready to recite the filter order from memory: `CharacterEncodingFilter` → `RateLimitFilter` → `SecurityContextHolderFilter` → `HeaderWriterFilter` → `CorsFilter` → `JwtAuthFilter` → `ExceptionTranslationFilter` → `AuthorizationFilter` → controller.)

**Cross-questions**:
1. *"Why does the SecurityContext get wiped after every request, even on a kept-alive connection?"* — Because HTTP is fundamentally stateless by design, and Spring Security deliberately doesn't fight that — every request is treated as a stranger; identity is *rebuilt from the token* every single time, never assumed to persist, which is exactly what makes this safe for a load-balanced, multi-instance deployment with no shared session state.
2. *"What's the difference between `401` and `403` in this flow, mechanically?"* — `401` means the `SecurityContext` never got an authentication set at all (missing/invalid/expired token — `JwtAuthFilter` never succeeded); `403` means authentication *did* succeed, but the resulting authorities don't satisfy the matched rule (`@PreAuthorize`/`authorizeHttpRequests`) — or, separately, a service-layer `AccessDeniedException` for object-level checks that role-based rules can't express.
3. *"Where exactly does `@PreAuthorize` get evaluated relative to the filter chain?"* — After the filter chain has already populated the `SecurityContext` (so by the time any `@PreAuthorize`-annotated method is even reached, Spring Security's method-security interceptor — enabled by `@EnableMethodSecurity` — evaluates the expression against the already-known authentication, immediately before the actual method body runs.

**System design angle**: understanding the filter chain precisely (not just "Spring Security handles it") is what separates "I used Spring Security" from "I understand what Spring Security is doing for me" — a frequent follow-up probe in backend interviews specifically because so many candidates can't go past the first sentence.

**Common mistakes**: saying "the JWT filter authenticates the user" without being able to explain *what object* actually gets stored as the principal, or that it's rebuilt fresh on literally every single request with zero server-side memory in between.

---

## SECTION 13 — HOW TO RUN THIS PROJECT

### Prerequisites

- Docker + Docker Compose (the only hard requirement for the steps below)
- Optional, for running the backend outside Docker: JDK 21, Maven (or use the bundled `./mvnw`)
- Optional, for running the frontend outside Docker: Node 18+

### Environment variables

Copy the example files and fill in real values where placeholders exist:

```bash
cp .env.example .env                       # compose-level vars (DB creds, Razorpay, JWT secret, CORS, delivery config)
cp backend/.env.example backend/.env        # used only if running the backend outside Docker
cp frontend/.env frontend/.env.local        # VITE_API_BASE_URL, defaults to http://localhost:8080 — usually fine as-is
```

At minimum, set real values for: `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` (any sufficiently long random string for local dev is fine), and — only if you actually need payments/uploads/email/SMS to work — `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET`/`RAZORPAY_WEBHOOK_SECRET`, `CLOUDINARY_*`, `MAIL_USERNAME`/`MAIL_PASSWORD` (a Gmail App Password, not your real Gmail password), and `FAST2SMS_API_KEY`. Everything else (registration, login, browsing products, categories, carts) works fine with placeholder values for those external services — you'll only hit errors when a flow specifically needs that integration.

### Run everything with Docker Compose

```bash
docker compose up --build       # first run, or after changing backend/frontend code or Dockerfiles
```

This starts, on the shared `bakeaura-network` bridge:

| Service | URL |
|---|---|
| Frontend (Nginx, built React app) | `http://localhost` |
| Backend API | `http://localhost:8080` |
| PostgreSQL | `localhost:5433` (mapped from container port 5432) |
| Redis | `localhost:6379` |

Other useful commands (from `Docker_commands.md`): `docker compose up -d` (background, no rebuild), `docker compose down` (stop everything), `docker compose down -v` (stop and **wipe the database volume** — use when you want a totally fresh schema), `docker compose logs -f` (tail logs live — the most useful one when something doesn't start).

### Verify it's actually working

1. **Health check**: `GET http://localhost:8080/actuator/health` — should return `{"status":"UP", ...}` with nested checks for `db` and `redis`. If either shows `DOWN`, that service didn't come up correctly — check `docker compose logs postgres` / `docker compose logs redis`.
2. **Register a user** (Postman or `curl`):
   ```bash
   curl -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{"name":"Sneha","email":"sneha@example.com","password":"password123"}'
   ```
   You should get back `{"success":true, ..., "data":{"accessToken":"...", "refreshToken":"...", "email":"sneha@example.com","role":"CUSTOMER"}}`.
3. **Call an authenticated endpoint** with that token:
   ```bash
   curl http://localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"
   ```
4. **In Postman specifically**: there's a `.postman/resources.yaml` already in the repo — import it as a starting collection, set a collection-level variable for the base URL and bearer token, and chain requests (save the access token from the login response into a variable, reuse it in the `Authorization` header of subsequent requests) rather than copy-pasting the token by hand every time.
5. **Test the role-application → become-a-seller flow end to end**: register a second user, log in as that user, `POST /api/role-applications` with `{"requestedRole":"SELLER","message":"..."}`, then log in as an `ADMIN` (you'll need to either seed one directly in Postgres — `UPDATE users SET role='ADMIN' WHERE email='...'` — or build the first admin manually, since there's no self-service "become an admin" path by design), and `POST /api/admin/role-applications/{id}/approve`. Confirm the original user can now `GET /api/products` create successfully (proving the seller-profile auto-provisioning from Section 6 actually ran).
6. **Frontend**: open `http://localhost` in a browser, register, log in, browse `/products` — open the browser's DevTools Network tab and confirm requests are going to `http://localhost:8080/api/...` with the `Authorization` header attached automatically.

### Running the backend outside Docker (for active development)

```bash
cd backend
./mvnw spring-boot:run        # picks up backend/.env if you're using a tool like direnv, or export the vars manually first
```

You'll need a locally-running Postgres and Redis (or point `SPRING_DATASOURCE_URL`/`REDIS_HOST` at the ones already running inside Docker via `docker compose up postgres redis` alone, then run just the backend natively against them for faster restart cycles while coding).

### Running the frontend outside Docker

```bash
cd frontend
npm install
npm run dev      # Vite dev server, default port 5173
```
