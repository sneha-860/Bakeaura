# Bakeaura 🎂

**A website that connects local home bakers with customers nearby — think of it as Instagram + Swiggy, but just for home bakers.**

Built by **Sneha Kalra**, final-year B.Tech IT student, as a placement portfolio project.

---

## What is Bakeaura, in plain words?

Imagine your neighbour bakes amazing cakes at home but has no way to sell them beyond word of mouth. Bakeaura gives them a shop window: customers nearby can find them, see their cakes, order online, pay safely, and track the delivery — just like ordering food on a delivery app.

There's also a fun AI feature: instead of scrolling through a menu, a customer can just **describe the cake they're dreaming of** in plain English ("a two-tier chocolate cake with red roses for my sister's birthday"), and the app uses Google's Gemini AI to draw a picture of it and write up the details, then sends that straight to a nearby baker as a custom order request.

---

## Who uses the app? (4 types of people)

| Who | What they do |
|---|---|
| **Customer** | Signs up with just a name, email and password. Browses cakes, adds to cart, pays, tracks delivery, leaves a review. |
| **Seller (the home baker)** | Starts out as a normal customer, then applies to become a seller. Once approved, they get their own shop page, add products, and manage incoming orders. |
| **Influencer** | Also starts as a customer, then applies to become an influencer. They get a personal referral code — when someone orders using their code, they earn a small commission. |
| **Admin** | Runs the whole platform behind the scenes — approves sellers/influencers, manages categories, and moderates the app. |

Everyone can browse the app freely, but you must **verify your email** before you're allowed to place an order or apply to become a seller/influencer.

---

## The main features

### 🎨 AI Cake Design Assistant (the star feature)
1. Customer types a description of their dream cake.
2. The AI (Google Gemini) writes up a design description and generates a preview picture — nothing is saved yet, so the customer can regenerate freely if they don't like it.
3. Once happy, the customer confirms — only then is the image saved and the request sent to a nearby baker.

*Why show a preview first? So the app doesn't fill up with junk requests from people who were just experimenting.*

### 🛒 Ordering and payment
- Customers can order for **right now** or **schedule ahead** (useful for birthday cakes ordered days in advance).
- Payment is handled by **Razorpay** (a trusted Indian payment gateway), the same technology real e-commerce apps use.
- Stock (how many cakes are left) is only actually reduced once payment is confirmed — so an abandoned payment never wrongly reduces stock.

### 📦 Order tracking
Every order moves through clear stages: **Placed → Confirmed → Preparing → Out for Delivery → Delivered**. The customer sees this update live on their screen (using a technology called WebSockets — the same idea behind live chat apps), and also gets an SMS/email at the important moments.

### ⭐ Reviews
Once an order is delivered, the customer can leave a rating and comment for the baker. This builds trust — new customers can see how good a baker is before ordering.

### 🎥 Reels (short videos)
Bakers can post short videos of their baking process, like Instagram Reels. Anyone can watch (no login needed), and the more recent, liked, and viewed a reel is — plus how well-rated the baker is — the higher it's ranked in the feed.

### 🤝 Influencer referrals
Influencers share their referral code. When a customer uses it at checkout, the influencer automatically earns a small commission, which they can later request to withdraw to their bank/UPI account.

### 🔔 Notifications
Every important update reaches the user in up to four ways: saved in their notification inbox, pushed live if they're online, sent by email for major events, and sent by SMS for urgent delivery updates.

---

## How it's built (the technology, explained simply)

### Backend (the "engine" — handles all the logic and data)
| What | Technology | Why |
|---|---|---|
| Programming language | Java 21 | Widely used, reliable for large apps |
| Framework | Spring Boot | The most common toolkit for building Java web servers |
| Login security | JWT tokens + BCrypt password hashing | Keeps passwords and sessions secure |
| Database | PostgreSQL | Stores all the real data — users, orders, products, etc. |
| Fast temporary storage | Redis | Stores things that need to be quick, like the shopping cart |
| Payments | Razorpay | Handles real payment processing safely |
| Image/video storage | Cloudinary | Stores product photos and reel videos |
| AI | Google Gemini (via Spring AI) | Powers the Cake Design Assistant |
| Live updates | WebSockets (STOMP/SockJS) | Sends real-time order status updates to the browser |
| Emails | Gmail SMTP | Sends verification and order emails |
| SMS | Fast2SMS | Sends delivery SMS alerts |

### Frontend (what the user actually sees and clicks)
| What | Technology | Why |
|---|---|---|
| UI framework | React 18 + Vite | Builds the interactive website |
| Page navigation | React Router | Moves between pages without reloading |
| Talking to the backend | Axios | Sends requests to the server, auto-refreshes login when it expires |
| Forms | React Hook Form + Zod | Validates what the user types before submitting |
| Charts | Recharts | Shows sales/earnings graphs to sellers and influencers |
| Styling | Plain CSS (a warm cream/espresso/gold theme) | Custom-designed look, not a template |
| Location | Browser GPS + OpenStreetMap | Figures out where the customer is, for free, without Google Maps |

**Note:** Bakeaura does **not** use Google Maps anywhere — distance between customer and baker is calculated with simple, free math (the "Haversine formula") instead of a paid API.

---

## How the pieces talk to each other (architecture)

```
   Your Browser (React website)
            │
            │  (normal requests + live updates)
            ▼
   Spring Boot server (Java)
            │
     ┌──────┴──────┐
     ▼             ▼
 PostgreSQL      Redis
 (permanent      (fast temporary
  data)           data — cart, sessions)
```

The server also talks to outside services when needed: Cloudinary (images), Razorpay (payments), Google Gemini (AI), Fast2SMS (texts), Gmail (emails).

**Design style: "modular monolith."** In plain terms — it's one single application (easy to build and run), but internally it's split into clean, separate sections (orders, payments, reviews, etc.) that don't reach into each other's data directly. This means any section could later be pulled out into its own separate service without breaking everything else — the best of both worlds for a project this size.

---

## Project folders

```
Bakeaura/
├── backend/    → the Java/Spring Boot server
│   └── src/main/java/com/bakeaura/
│       ├── auth/          → login, signup, email verification
│       ├── ai/            → the Cake Design Assistant
│       ├── order/         → order placing and tracking
│       ├── payment/       → Razorpay payments
│       ├── product/       → cake/product listings
│       ├── seller/        → baker shop profiles
│       ├── influencer/    → referral & collaboration system
│       ├── wallet/        → influencer earnings
│       ├── reel/          → short video posts
│       ├── notification/  → email/SMS/inbox alerts
│       └── ... (each feature has its own clearly separated folder)
│
└── frontend/   → the React website
    └── src/
        ├── pages/       → every screen the user sees
        ├── components/  → reusable pieces (buttons, cards, etc.)
        ├── api/         → code that talks to the backend
        └── store/       → keeps track of who's logged in
```

---

## Running it on your own computer

### You'll need
- Java 21 and Maven
- Node.js and npm
- PostgreSQL and Redis running locally
- Free accounts on: Cloudinary, Razorpay (test mode), Google AI Studio (Gemini key), Gmail (with an App Password), Fast2SMS (optional)

### Start the backend
```bash
cd backend
```
Create a file `backend/.env` (this file is never uploaded to GitHub — it holds secret keys) with your database, email, payment, and AI keys. Then run:
```bash
./mvnw spring-boot:run
```
The server starts at `http://localhost:8080`. It automatically creates all the database tables for you the first time it runs.

### Start the frontend
```bash
cd frontend
npm install
npm run dev
```
The website opens at `http://localhost:5173`.

---

## About the project

Bakeaura was built end-to-end — database design, backend APIs, security, real-time features, AI integration, and the full website — as a portfolio project to demonstrate full-stack development skills for Java Developer and full-stack job applications.

**GitHub:** [https://github.com/sneha-860/Bakeaura](https://github.com/sneha-860/Bakeaura)
