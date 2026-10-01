# Inama backend

This is the [AgriAI Advisory System](https://github.com/anvi-sha675/AgriAI-Advisory-System) backend, extended with the **Inama API v1** (`src/v1`) that the Inama Android app uses. The original AgriAI API is unchanged and documented below.

```bash
npm install
npm run dev:inama    # Inama API only, http://localhost:5055/api/v1: no MongoDB, no extra dependencies
npm start            # full Express server: AgriAI routes at /api/* (need MONGODB_URI) + Inama at /api/v1
npm test             # Inama API tests (node --test)
```

- Endpoints and payloads: [../docs/API.md](../docs/API.md)
- Design decisions: [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)
- What was reused from AgriAI and what is new: [../docs/REUSE.md](../docs/REUSE.md)
- Settings: `.env.example` (all AI and weather keys are optional; without them the API uses the shared knowledge base and a sample forecast)

---

# Original AgriAI documentation

## AgriAI Advisory System — Backend API

Node.js + Express + MongoDB REST API for the AgriAI Advisory System.
Integrates Google Gemini AI for intelligent farming advisory and OpenWeatherMap for weather data.

---

## Tech Stack

| Layer          | Technology                       |
| -------------- | -------------------------------- |
| Runtime        | Node.js 18+                      |
| Framework      | Express 4                        |
| Database       | MongoDB (via Mongoose 8)         |
| Authentication | JWT (jsonwebtoken) + bcryptjs    |
| AI             | Google Gemini 1.5 Flash API      |
| Weather        | OpenWeatherMap API               |
| File Upload    | multer (memory storage)          |
| Security       | helmet, cors, express-rate-limit |

---

## Project Structure

```
agriai-backend/
├── config/
│   └── index.js              # Env var loading + validation
├── src/
│   ├── server.js             # Entry point — connects DB, starts HTTP server
│   ├── app.js                # Express app — middleware + all routes
│   ├── models/
│   │   ├── User.js           # User schema (bcrypt pre-save hook, toSafeObject)
│   │   ├── Chat.js           # Chat + embedded Message schema
│   │   ├── Bookmark.js       # Saved responses
│   │   ├── Notification.js   # User notifications
│   │   ├── DiseaseResult.js  # Disease detection history
│   │   └── index.js          # Clean barrel export
│   ├── controllers/
│   │   ├── authController.js
│   │   ├── chatController.js
│   │   ├── diseaseController.js
│   │   ├── featureControllers.js  # crop, soil, weather, notifs, bookmarks, schemes
│   │   └── adminController.js
│   ├── routes/
│   │   ├── auth.js
│   │   ├── chat.js
│   │   ├── disease.js
│   │   ├── features.js
│   │   └── admin.js
│   ├── services/
│   │   ├── geminiService.js   # Gemini API wrapper + structured fallbacks
│   │   └── weatherService.js  # OpenWeatherMap wrapper + fallback
│   ├── middleware/
│   │   ├── auth.js            # protect() + restrictTo()
│   │   ├── errorHandler.js    # Global error + 404
│   │   └── logger.js          # Dev request logger
│   └── utils/
│       ├── db.js              # MongoDB connection + disconnect
│       ├── response.js        # sendSuccess, sendError, helpers
│       └── seed.js            # Database seeder (npm run seed)
├── .env.example
├── .gitignore
└── package.json
```

---

## Getting Started

### 1. Prerequisites

- Node.js 18+
- MongoDB running locally **or** a MongoDB Atlas cluster (free tier available)

### 2. Install dependencies

```bash
npm install
```

### 3. Set environment variables

```bash
cp .env.example .env
```

Edit `.env`:

```env
PORT=5000
NODE_ENV=development

# MongoDB
MONGODB_URI=mongodb://localhost:27017/agriai
# Atlas example:
# MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/agriai

# JWT
JWT_SECRET=your_long_random_secret_minimum_32_chars
JWT_EXPIRES_IN=7d

# Gemini AI (free tier — get at https://aistudio.google.com/app/apikey)
GEMINI_API_KEY=your_gemini_key

# OpenWeatherMap (free tier — get at https://openweathermap.org/api)
WEATHER_API_KEY=your_weather_key

# Frontend URL (for CORS)
FRONTEND_URL=http://localhost:5173
```

> **Note:** The server starts and all endpoints respond even without
> `GEMINI_API_KEY` and `WEATHER_API_KEY`. AI responses and weather will use
> realistic fallback data. Only MongoDB is required.

### 4. Seed the database (optional but recommended)

```bash
npm run seed
```

Creates 3 demo users, 3 sample chat conversations, and 4 notifications.

### 5. Start the server

```bash
# Development (auto-restart on file changes)
npm run dev

# Production
npm start
```

Server runs at `http://localhost:5000`

---

## Demo Credentials

| Role   | Email              | Password   |
| ------ | ------------------ | ---------- |
| Farmer | ramesh@example.com | Demo@1234  |
| Farmer | sita@example.com   | Demo@1234  |
| Admin  | admin@agriai.in    | Admin@1234 |

---

## API Reference

All responses follow this shape:

```json
{ "success": true, "message": "...", "data": { ... } }
```

All protected routes require:

```
Authorization: Bearer <token>
```

---

### Health Check

```
GET /api/health
```

No auth required. Use to confirm server is running.

---

### Auth

| Method | Endpoint                    | Auth | Description               |
| ------ | --------------------------- | ---- | ------------------------- |
| POST   | `/api/auth/register`        | No   | Create new farmer account |
| POST   | `/api/auth/login`           | No   | Login, returns JWT + user |
| GET    | `/api/auth/me`              | Yes  | Get current user profile  |
| PATCH  | `/api/auth/me`              | Yes  | Update profile            |
| PATCH  | `/api/auth/change-password` | Yes  | Change password           |

**Register body:**

```json
{
  "fullName": "Ramesh Patil",
  "email": "ramesh@example.com",
  "phone": "9876543210",
  "password": "Demo@1234",
  "confirmPassword": "Demo@1234"
}
```

**Login body:**

```json
{ "email": "ramesh@example.com", "password": "Demo@1234" }
```

**Login response data:**

```json
{
  "token": "eyJhbGci...",
  "user": {
    "id": "...",
    "name": "Ramesh Patil",
    "email": "ramesh@example.com",
    "role": "farmer",
    "location": "Nashik, Maharashtra",
    "primaryCrops": ["Wheat", "Onion"],
    "preferredLanguage": "Hindi"
  }
}
```

---

### Chat (AI Advisory)

| Method | Endpoint              | Auth | Description                           |
| ------ | --------------------- | ---- | ------------------------------------- |
| POST   | `/api/chat`           | Yes  | Send message, get AI response         |
| GET    | `/api/chat`           | Yes  | List all conversations                |
| GET    | `/api/chat/:id`       | Yes  | Get single conversation with messages |
| DELETE | `/api/chat/:id`       | Yes  | Delete conversation                   |
| PATCH  | `/api/chat/:id/title` | Yes  | Rename conversation                   |

**Send message — start new conversation:**

```json
{ "message": "My wheat leaves are turning yellow" }
```

**Send message — continue existing conversation:**

```json
{
  "message": "Does this apply to barley too?",
  "chatId": "64f2a..."
}
```

**Response data:**

```json
{
  "chatId": "64f2a...",
  "message": {
    "role": "assistant",
    "content": "Yellowing wheat leaves usually indicate...",
    "reply": "Yellowing wheat leaves usually indicate...",
    "causes": ["Nitrogen deficiency", "Yellow rust"],
    "treatment": ["Apply urea 40–50 kg/acre", "Spray Propiconazole"],
    "prevention": ["Use rust-resistant varieties"]
  }
}
```

---

### Disease Detection

| Method | Endpoint                 | Auth | Description                |
| ------ | ------------------------ | ---- | -------------------------- |
| POST   | `/api/disease-detection` | Yes  | Analyse crop image         |
| GET    | `/api/disease-detection` | Yes  | Get past detection results |

**Request:** `multipart/form-data` with field `image` (JPEG/PNG/WebP, max 10MB)

**Response data:**

```json
{
  "disease": "Late Blight (Phytophthora infestans)",
  "confidence": 87,
  "crop": "Potato / Tomato",
  "severity": "High",
  "causes": ["Prolonged leaf wetness", "High humidity"],
  "remedies": ["Apply copper-based fungicide", "Remove infected debris"],
  "analysedByAI": true
}
```

---

### Crop Recommendation

```
POST /api/crop-recommendation
```

**Body:**

```json
{
  "soilType": "Loamy",
  "season": "Kharif",
  "location": "Nashik, Maharashtra"
}
```

Valid `soilType` values: `Loamy`, `Sandy`, `Clayey`, `Black`, `Red`

---

### Soil Health Advisory

```
POST /api/soil-health
```

**Body:**

```json
{ "ph": 6.8, "nitrogen": 45, "phosphorus": 30, "potassium": 50 }
```

---

### Weather

```
GET /api/weather?location=Nashik,IN
```

`location` is optional — defaults to the logged-in user's stored location.

---

### Notifications

| Method | Endpoint                           | Auth | Description        |
| ------ | ---------------------------------- | ---- | ------------------ |
| GET    | `/api/notifications`               | Yes  | List notifications |
| PATCH  | `/api/notifications/mark-all-read` | Yes  | Mark all as read   |
| PATCH  | `/api/notifications/:id/read`      | Yes  | Mark one as read   |

**Query params:** `?type=weather|pest|chat|scheme|system`, `?unread=true`

---

### Bookmarks

| Method | Endpoint                          | Auth | Description          |
| ------ | --------------------------------- | ---- | -------------------- |
| GET    | `/api/bookmarks`                  | Yes  | List saved bookmarks |
| POST   | `/api/bookmarks`                  | Yes  | Save a bookmark      |
| DELETE | `/api/bookmarks/:id`              | Yes  | Remove by ID         |
| DELETE | `/api/bookmarks/source/:sourceId` | Yes  | Remove by source ID  |

**Add bookmark body:**

```json
{
  "type": "chat",
  "title": "Wheat yellowing advice",
  "summary": "Nitrogen deficiency or early rust...",
  "tags": ["wheat", "disease"],
  "sourcePath": "/chat",
  "sourceId": "msg_abc123"
}
```

Valid `type` values: `chat`, `disease`, `crop`, `soil`

---

### Government Schemes

| Method | Endpoint           | Auth | Description       |
| ------ | ------------------ | ---- | ----------------- |
| GET    | `/api/schemes`     | Yes  | List schemes      |
| GET    | `/api/schemes/:id` | Yes  | Get single scheme |

**Query params:** `?search=kisan`, `?category=Crop Insurance`, `?status=open|closed`

---

### Admin (role: admin only)

| Method | Endpoint                      | Auth  | Description                           |
| ------ | ----------------------------- | ----- | ------------------------------------- |
| GET    | `/api/admin/stats`            | Admin | Platform statistics + recent activity |
| GET    | `/api/admin/users`            | Admin | List all users with query counts      |
| GET    | `/api/admin/users/:id`        | Admin | Get single user detail                |
| PATCH  | `/api/admin/users/:id/status` | Admin | Set active/inactive/suspended         |
| DELETE | `/api/admin/users/:id`        | Admin | Remove user + cascade delete data     |
| GET    | `/api/admin/chats`            | Admin | All chat logs (paginated)             |

---

## MongoDB Models

### User

```
name, email (unique), phone, password (hashed),
role (farmer|admin), status (active|inactive|suspended),
location, preferredLanguage, primaryCrops[], farmSize, joinedOn
```

### Chat

```
userId (ref: User), title,
messages: [{ role, content, reply, causes[], treatment[], prevention[] }]
```

### Bookmark

```
userId (ref: User), type (chat|disease|crop|soil),
title, summary, tags[], sourcePath, sourceId
Index: (userId + sourceId) unique — prevents duplicate bookmarks
```

### Notification

```
userId (ref: User), type (weather|pest|chat|scheme|system),
title, message, read (bool)
```

### DiseaseResult

```
userId (ref: User), disease, confidence, crop, severity,
causes[], remedies[], analysedByAI, imageSize, imageMimeType
```

---

## Connecting the Frontend

The frontend's `src/services/aiService.js` and `src/context/AuthContext.jsx`
already point to this backend via `VITE_API_URL`. Make sure you have:

**Frontend `.env.local`:**

```
VITE_API_URL=http://localhost:5000/api
```

Both the frontend and backend use a graceful fallback pattern — if the
backend is unreachable, the frontend automatically falls back to local
mock data so demos work without a running server.

---

## Deployment Notes

**Backend (Railway / Render / EC2):**

- Set all `.env` variables in the hosting platform's environment panel
- Set `NODE_ENV=production`
- Set `FRONTEND_URL` to your deployed frontend URL
- MongoDB Atlas connection string goes in `MONGODB_URI`

**Frontend (Vercel / Netlify):**

- Set `VITE_API_URL` to your deployed backend URL

**MongoDB Atlas (free M0 tier):**

1. Create cluster at mongodb.com/atlas
2. Create a database user
3. Whitelist `0.0.0.0/0` in Network Access (or your server IP)
4. Copy the connection string into `MONGODB_URI`
