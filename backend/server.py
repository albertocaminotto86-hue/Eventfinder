from fastapi import FastAPI, APIRouter, HTTPException, Request, Response, Depends
from fastapi.responses import JSONResponse
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
from pathlib import Path
from pydantic import BaseModel, Field
from typing import List, Optional
import uuid
from datetime import datetime, timezone, timedelta
import httpx
import math

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

# MongoDB connection
mongo_url = os.environ['MONGO_URL']
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ['DB_NAME']]

# Create the main app
app = FastAPI()

# Create a router with the /api prefix
api_router = APIRouter(prefix="/api")

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ============ MODELS ============

class User(BaseModel):
    user_id: str
    email: str
    name: str
    picture: Optional[str] = None
    favorite_categories: List[str] = []
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class UserSession(BaseModel):
    session_id: str = Field(default_factory=lambda: f"session_{uuid.uuid4().hex[:16]}")
    user_id: str
    session_token: str
    expires_at: datetime
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class Event(BaseModel):
    event_id: str = Field(default_factory=lambda: f"evt_{uuid.uuid4().hex[:12]}")
    title: str
    description: str
    ai_description: Optional[str] = None
    category: str  # music, sport, art, food, nightlife, networking, teatro, outdoor, famiglia
    lat: float
    lng: float
    address: str
    city: str
    date: datetime
    end_date: Optional[datetime] = None
    price: float = 0.0
    is_free: bool = True
    source: str  # eventbrite, meetup, facebook, whatsup
    source_url: Optional[str] = None
    image_url: Optional[str] = None
    participants_count: int = 0
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class EventResponse(BaseModel):
    event_id: str
    title: str
    description: str
    ai_description: Optional[str] = None
    category: str
    lat: float
    lng: float
    address: str
    city: str
    date: str
    end_date: Optional[str] = None
    price: float
    is_free: bool
    source: str
    source_url: Optional[str] = None
    image_url: Optional[str] = None
    participants_count: int
    distance_km: Optional[float] = None
    created_at: str

class RSVP(BaseModel):
    rsvp_id: str = Field(default_factory=lambda: f"rsvp_{uuid.uuid4().hex[:12]}")
    user_id: str
    event_id: str
    status: str = "confirmed"  # confirmed, cancelled
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class SavedEvent(BaseModel):
    saved_id: str = Field(default_factory=lambda: f"saved_{uuid.uuid4().hex[:12]}")
    user_id: str
    event_id: str
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

# ============ HELPER FUNCTIONS ============

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate distance in km between two points."""
    R = 6371  # Earth's radius in km
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda/2)**2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))

async def get_current_user(request: Request) -> Optional[User]:
    """Get current user from session token in cookies or Authorization header."""
    session_token = request.cookies.get("session_token")
    if not session_token:
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            session_token = auth_header.split(" ")[1]
    
    if not session_token:
        return None
    
    session_doc = await db.user_sessions.find_one(
        {"session_token": session_token},
        {"_id": 0}
    )
    
    if not session_doc:
        return None
    
    expires_at = session_doc["expires_at"]
    if isinstance(expires_at, str):
        expires_at = datetime.fromisoformat(expires_at)
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        return None
    
    user_doc = await db.users.find_one(
        {"user_id": session_doc["user_id"]},
        {"_id": 0}
    )
    
    if not user_doc:
        return None
    
    return User(**user_doc)

async def require_auth(request: Request) -> User:
    """Require authenticated user."""
    user = await get_current_user(request)
    if not user:
        raise HTTPException(status_code=401, detail="Non autenticato")
    return user

# ============ AUTH ENDPOINTS ============

@api_router.post("/auth/session")
async def exchange_session(request: Request, response: Response):
    """Exchange session_id for session_token after Google OAuth."""
    body = await request.json()
    session_id = body.get("session_id")
    
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id richiesto")
    
    # Call Emergent Auth to get user data
    async with httpx.AsyncClient() as client_http:
        resp = await client_http.get(
            "https://demobackend.emergentagent.com/auth/v1/env/oauth/session-data",
            headers={"X-Session-ID": session_id}
        )
        
        if resp.status_code != 200:
            raise HTTPException(status_code=401, detail="Sessione non valida")
        
        user_data = resp.json()
    
    # Check if user exists
    existing_user = await db.users.find_one(
        {"email": user_data["email"]},
        {"_id": 0}
    )
    
    if existing_user:
        user_id = existing_user["user_id"]
        # Update user info
        await db.users.update_one(
            {"user_id": user_id},
            {"$set": {
                "name": user_data["name"],
                "picture": user_data.get("picture")
            }}
        )
    else:
        # Create new user
        user_id = f"user_{uuid.uuid4().hex[:12]}"
        new_user = {
            "user_id": user_id,
            "email": user_data["email"],
            "name": user_data["name"],
            "picture": user_data.get("picture"),
            "favorite_categories": [],
            "created_at": datetime.now(timezone.utc).isoformat()
        }
        await db.users.insert_one(new_user)
    
    # Create session
    session_token = user_data.get("session_token", f"st_{uuid.uuid4().hex}")
    expires_at = datetime.now(timezone.utc) + timedelta(days=7)
    
    session_doc = {
        "session_id": f"session_{uuid.uuid4().hex[:16]}",
        "user_id": user_id,
        "session_token": session_token,
        "expires_at": expires_at.isoformat(),
        "created_at": datetime.now(timezone.utc).isoformat()
    }
    await db.user_sessions.insert_one(session_doc)
    
    # Set cookie
    response.set_cookie(
        key="session_token",
        value=session_token,
        path="/",
        secure=True,
        httponly=True,
        samesite="none",
        max_age=7 * 24 * 60 * 60
    )
    
    # Get user doc
    user_doc = await db.users.find_one({"user_id": user_id}, {"_id": 0})
    
    return {"user": user_doc, "session_token": session_token}

@api_router.get("/auth/me")
async def get_me(request: Request):
    """Get current authenticated user."""
    user = await get_current_user(request)
    if not user:
        raise HTTPException(status_code=401, detail="Non autenticato")
    return user.model_dump()

@api_router.post("/auth/logout")
async def logout(request: Request, response: Response):
    """Logout user."""
    session_token = request.cookies.get("session_token")
    if session_token:
        await db.user_sessions.delete_one({"session_token": session_token})
    
    response.delete_cookie(key="session_token", path="/")
    return {"message": "Logout effettuato"}

# ============ EVENTS ENDPOINTS ============

@api_router.get("/events", response_model=List[EventResponse])
async def get_events(
    lat: Optional[float] = None,
    lng: Optional[float] = None,
    distance_km: Optional[float] = None,
    category: Optional[str] = None,
    date_filter: Optional[str] = None,  # today, tomorrow, weekend, all
    is_free: Optional[bool] = None,
    limit: int = 50
):
    """Get events with optional filters."""
    query = {}
    
    # Category filter
    if category and category != "all":
        categories = category.split(",")
        query["category"] = {"$in": categories}
    
    # Price filter
    if is_free is not None:
        query["is_free"] = is_free
    
    # Date filter
    now = datetime.now(timezone.utc)
    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    
    if date_filter == "today":
        today_end = today_start + timedelta(days=1)
        query["date"] = {"$gte": today_start.isoformat(), "$lt": today_end.isoformat()}
    elif date_filter == "tomorrow":
        tomorrow_start = today_start + timedelta(days=1)
        tomorrow_end = tomorrow_start + timedelta(days=1)
        query["date"] = {"$gte": tomorrow_start.isoformat(), "$lt": tomorrow_end.isoformat()}
    elif date_filter == "weekend":
        # Find next Saturday and Sunday
        days_until_saturday = (5 - now.weekday()) % 7
        if days_until_saturday == 0 and now.weekday() == 5:
            days_until_saturday = 0
        elif days_until_saturday == 0:
            days_until_saturday = 7
        saturday = today_start + timedelta(days=days_until_saturday)
        monday = saturday + timedelta(days=2)
        query["date"] = {"$gte": saturday.isoformat(), "$lt": monday.isoformat()}
    
    # Get events
    events = await db.events.find(query, {"_id": 0}).limit(limit).to_list(limit)
    
    result = []
    for event in events:
        # Calculate distance if user location provided
        distance = None
        if lat is not None and lng is not None:
            distance = haversine_distance(lat, lng, event["lat"], event["lng"])
            
            # Filter by distance if specified
            if distance_km is not None and distance > distance_km:
                continue
        
        event_resp = EventResponse(
            event_id=event["event_id"],
            title=event["title"],
            description=event["description"],
            ai_description=event.get("ai_description"),
            category=event["category"],
            lat=event["lat"],
            lng=event["lng"],
            address=event["address"],
            city=event["city"],
            date=event["date"] if isinstance(event["date"], str) else event["date"].isoformat(),
            end_date=event.get("end_date") if isinstance(event.get("end_date"), str) else (event.get("end_date").isoformat() if event.get("end_date") else None),
            price=event.get("price", 0.0),
            is_free=event.get("is_free", True),
            source=event["source"],
            source_url=event.get("source_url"),
            image_url=event.get("image_url"),
            participants_count=event.get("participants_count", 0),
            distance_km=round(distance, 2) if distance else None,
            created_at=event["created_at"] if isinstance(event["created_at"], str) else event["created_at"].isoformat()
        )
        result.append(event_resp)
    
    # Sort by distance if location provided
    if lat is not None and lng is not None:
        result.sort(key=lambda x: x.distance_km if x.distance_km else float('inf'))
    
    return result

@api_router.get("/events/{event_id}", response_model=EventResponse)
async def get_event(event_id: str, lat: Optional[float] = None, lng: Optional[float] = None):
    """Get single event by ID."""
    event = await db.events.find_one({"event_id": event_id}, {"_id": 0})
    
    if not event:
        raise HTTPException(status_code=404, detail="Evento non trovato")
    
    distance = None
    if lat is not None and lng is not None:
        distance = haversine_distance(lat, lng, event["lat"], event["lng"])
    
    return EventResponse(
        event_id=event["event_id"],
        title=event["title"],
        description=event["description"],
        ai_description=event.get("ai_description"),
        category=event["category"],
        lat=event["lat"],
        lng=event["lng"],
        address=event["address"],
        city=event["city"],
        date=event["date"] if isinstance(event["date"], str) else event["date"].isoformat(),
        end_date=event.get("end_date") if isinstance(event.get("end_date"), str) else (event.get("end_date").isoformat() if event.get("end_date") else None),
        price=event.get("price", 0.0),
        is_free=event.get("is_free", True),
        source=event["source"],
        source_url=event.get("source_url"),
        image_url=event.get("image_url"),
        participants_count=event.get("participants_count", 0),
        distance_km=round(distance, 2) if distance else None,
        created_at=event["created_at"] if isinstance(event["created_at"], str) else event["created_at"].isoformat()
    )

# ============ RSVP ENDPOINTS ============

@api_router.post("/events/{event_id}/rsvp")
async def create_rsvp(event_id: str, request: Request):
    """Create RSVP for an event."""
    user = await require_auth(request)
    
    # Check event exists
    event = await db.events.find_one({"event_id": event_id}, {"_id": 0})
    if not event:
        raise HTTPException(status_code=404, detail="Evento non trovato")
    
    # Check existing RSVP
    existing = await db.rsvps.find_one(
        {"user_id": user.user_id, "event_id": event_id, "status": "confirmed"},
        {"_id": 0}
    )
    if existing:
        raise HTTPException(status_code=400, detail="Già confermato per questo evento")
    
    rsvp = RSVP(user_id=user.user_id, event_id=event_id)
    rsvp_doc = rsvp.model_dump()
    rsvp_doc["created_at"] = rsvp_doc["created_at"].isoformat()
    await db.rsvps.insert_one(rsvp_doc)
    
    # Increment participants count
    await db.events.update_one(
        {"event_id": event_id},
        {"$inc": {"participants_count": 1}}
    )
    
    return {"message": "RSVP confermato", "rsvp_id": rsvp.rsvp_id}

@api_router.delete("/events/{event_id}/rsvp")
async def cancel_rsvp(event_id: str, request: Request):
    """Cancel RSVP for an event."""
    user = await require_auth(request)
    
    result = await db.rsvps.update_one(
        {"user_id": user.user_id, "event_id": event_id, "status": "confirmed"},
        {"$set": {"status": "cancelled"}}
    )
    
    if result.modified_count > 0:
        await db.events.update_one(
            {"event_id": event_id},
            {"$inc": {"participants_count": -1}}
        )
    
    return {"message": "RSVP cancellato"}

@api_router.get("/events/{event_id}/rsvp-status")
async def get_rsvp_status(event_id: str, request: Request):
    """Check if current user has RSVP for event."""
    user = await get_current_user(request)
    if not user:
        return {"has_rsvp": False}
    
    rsvp = await db.rsvps.find_one(
        {"user_id": user.user_id, "event_id": event_id, "status": "confirmed"},
        {"_id": 0}
    )
    
    return {"has_rsvp": rsvp is not None}

# ============ SAVED EVENTS ============

@api_router.post("/events/{event_id}/save")
async def save_event(event_id: str, request: Request):
    """Save an event."""
    user = await require_auth(request)
    
    # Check event exists
    event = await db.events.find_one({"event_id": event_id}, {"_id": 0})
    if not event:
        raise HTTPException(status_code=404, detail="Evento non trovato")
    
    # Check if already saved
    existing = await db.saved_events.find_one(
        {"user_id": user.user_id, "event_id": event_id},
        {"_id": 0}
    )
    if existing:
        raise HTTPException(status_code=400, detail="Evento già salvato")
    
    saved = SavedEvent(user_id=user.user_id, event_id=event_id)
    saved_doc = saved.model_dump()
    saved_doc["created_at"] = saved_doc["created_at"].isoformat()
    await db.saved_events.insert_one(saved_doc)
    
    return {"message": "Evento salvato", "saved_id": saved.saved_id}

@api_router.delete("/events/{event_id}/save")
async def unsave_event(event_id: str, request: Request):
    """Remove saved event."""
    user = await require_auth(request)
    
    await db.saved_events.delete_one(
        {"user_id": user.user_id, "event_id": event_id}
    )
    
    return {"message": "Evento rimosso dai salvati"}

@api_router.get("/events/{event_id}/save-status")
async def get_save_status(event_id: str, request: Request):
    """Check if current user has saved event."""
    user = await get_current_user(request)
    if not user:
        return {"is_saved": False}
    
    saved = await db.saved_events.find_one(
        {"user_id": user.user_id, "event_id": event_id},
        {"_id": 0}
    )
    
    return {"is_saved": saved is not None}

# ============ USER PROFILE ============

@api_router.get("/user/saved-events")
async def get_saved_events(request: Request):
    """Get user's saved events."""
    user = await require_auth(request)
    
    saved = await db.saved_events.find(
        {"user_id": user.user_id},
        {"_id": 0}
    ).to_list(100)
    
    event_ids = [s["event_id"] for s in saved]
    events = await db.events.find(
        {"event_id": {"$in": event_ids}},
        {"_id": 0}
    ).to_list(100)
    
    result = []
    for event in events:
        result.append(EventResponse(
            event_id=event["event_id"],
            title=event["title"],
            description=event["description"],
            ai_description=event.get("ai_description"),
            category=event["category"],
            lat=event["lat"],
            lng=event["lng"],
            address=event["address"],
            city=event["city"],
            date=event["date"] if isinstance(event["date"], str) else event["date"].isoformat(),
            end_date=event.get("end_date") if isinstance(event.get("end_date"), str) else (event.get("end_date").isoformat() if event.get("end_date") else None),
            price=event.get("price", 0.0),
            is_free=event.get("is_free", True),
            source=event["source"],
            source_url=event.get("source_url"),
            image_url=event.get("image_url"),
            participants_count=event.get("participants_count", 0),
            distance_km=None,
            created_at=event["created_at"] if isinstance(event["created_at"], str) else event["created_at"].isoformat()
        ))
    
    return result

@api_router.get("/user/rsvps")
async def get_user_rsvps(request: Request):
    """Get user's RSVP'd events."""
    user = await require_auth(request)
    
    rsvps = await db.rsvps.find(
        {"user_id": user.user_id, "status": "confirmed"},
        {"_id": 0}
    ).to_list(100)
    
    event_ids = [r["event_id"] for r in rsvps]
    events = await db.events.find(
        {"event_id": {"$in": event_ids}},
        {"_id": 0}
    ).to_list(100)
    
    result = []
    for event in events:
        result.append(EventResponse(
            event_id=event["event_id"],
            title=event["title"],
            description=event["description"],
            ai_description=event.get("ai_description"),
            category=event["category"],
            lat=event["lat"],
            lng=event["lng"],
            address=event["address"],
            city=event["city"],
            date=event["date"] if isinstance(event["date"], str) else event["date"].isoformat(),
            end_date=event.get("end_date") if isinstance(event.get("end_date"), str) else (event.get("end_date").isoformat() if event.get("end_date") else None),
            price=event.get("price", 0.0),
            is_free=event.get("is_free", True),
            source=event["source"],
            source_url=event.get("source_url"),
            image_url=event.get("image_url"),
            participants_count=event.get("participants_count", 0),
            distance_km=None,
            created_at=event["created_at"] if isinstance(event["created_at"], str) else event["created_at"].isoformat()
        ))
    
    return result

@api_router.put("/user/preferences")
async def update_preferences(request: Request):
    """Update user preferences."""
    user = await require_auth(request)
    body = await request.json()
    
    favorite_categories = body.get("favorite_categories", [])
    
    await db.users.update_one(
        {"user_id": user.user_id},
        {"$set": {"favorite_categories": favorite_categories}}
    )
    
    return {"message": "Preferenze aggiornate"}

# ============ AI REWRITE ENDPOINT ============

@api_router.post("/ai/rewrite-description")
async def rewrite_description(request: Request):
    """Rewrite event description using AI."""
    body = await request.json()
    original_description = body.get("description", "")
    title = body.get("title", "")
    category = body.get("category", "")
    
    if not original_description:
        raise HTTPException(status_code=400, detail="Descrizione richiesta")
    
    try:
        from emergentintegrations.llm.chat import LlmChat, UserMessage
        
        api_key = os.environ.get("EMERGENT_LLM_KEY")
        if not api_key:
            raise HTTPException(status_code=500, detail="API key non configurata")
        
        chat = LlmChat(
            api_key=api_key,
            session_id=f"rewrite_{uuid.uuid4().hex[:8]}",
            system_message="""Sei un copywriter italiano esperto in eventi. Il tuo compito è riscrivere le descrizioni degli eventi in modo più chiaro, coinvolgente e adatto al formato mobile.

Regole:
- Massimo 2-3 frasi brevi
- Tono entusiasta ma non esagerato
- Evidenzia cosa rende l'evento speciale
- Usa parole italiane colloquiali ma corrette
- Non usare emoji
- Inizia con un verbo d'azione o una domanda retorica"""
        ).with_model("anthropic", "claude-sonnet-4-5-20250929")
        
        user_message = UserMessage(
            text=f"""Riscrivi questa descrizione evento in italiano:

Titolo: {title}
Categoria: {category}
Descrizione originale: {original_description}

Rispondi solo con la nuova descrizione, nient'altro."""
        )
        
        response = await chat.send_message(user_message)
        
        return {"ai_description": response.strip()}
        
    except Exception as e:
        logger.error(f"AI rewrite error: {e}")
        raise HTTPException(status_code=500, detail="Errore durante la riscrittura")

# ============ CATEGORIES ============

@api_router.get("/categories")
async def get_categories():
    """Get all event categories with colors."""
    return {
        "categories": [
            {"id": "music", "name": "Musica", "color": "#FF4500"},
            {"id": "sport", "name": "Sport", "color": "#00A86B"},
            {"id": "art", "name": "Arte", "color": "#0057B7"},
            {"id": "food", "name": "Food & Drink", "color": "#FFD700"},
            {"id": "nightlife", "name": "Nightlife", "color": "#800080"},
            {"id": "networking", "name": "Networking", "color": "#FF6B33"},
            {"id": "teatro", "name": "Teatro", "color": "#D32F2F"},
            {"id": "outdoor", "name": "Outdoor", "color": "#2E8B57"},
            {"id": "famiglia", "name": "Famiglia", "color": "#FF69B4"},
            {"id": "altro", "name": "Altro", "color": "#808080"}
        ]
    }

# ============ SEED MOCK DATA ============

@api_router.post("/seed-events")
async def seed_events():
    """Seed database with mock events."""
    # Clear existing events
    await db.events.delete_many({})
    
    now = datetime.now(timezone.utc)
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    
    mock_events = [
        # Roma
        {
            "event_id": "evt_roma_001",
            "title": "Jazz al Tramonto - Terrazza Pincio",
            "description": "Serata jazz con vista mozzafiato su Roma. Aperitivo incluso nel biglietto.",
            "ai_description": "Vivi la magia di Roma al tramonto con le note del jazz che si fondono con il panorama della Città Eterna. Aperitivo sotto le stelle incluso.",
            "category": "music",
            "lat": 41.9109,
            "lng": 12.4823,
            "address": "Terrazza del Pincio",
            "city": "Roma",
            "date": (today + timedelta(hours=19)).isoformat(),
            "price": 15.0,
            "is_free": False,
            "source": "eventbrite",
            "source_url": "https://eventbrite.it",
            "image_url": "https://images.unsplash.com/photo-1768405031181-33bb016e2fbb?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjAzMzl8MHwxfHNlYXJjaHwzfHxjb25jZXJ0JTIwY3Jvd2QlMjBlbmVyZ3klMjBuaWdodCUyMGxpZ2h0fGVufDB8fHx8MTc3NDEzMzExNnww&ixlib=rb-4.1.0&q=85",
            "participants_count": 47,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_roma_002",
            "title": "Street Food Festival Trastevere",
            "description": "I migliori food truck della capitale in un weekend di sapori. Ingresso libero.",
            "ai_description": "Scopri i sapori più autentici di Roma in un unico posto. Food truck, birre artigianali e atmosfera unica a Trastevere.",
            "category": "food",
            "lat": 41.8897,
            "lng": 12.4692,
            "address": "Piazza Trilussa",
            "city": "Roma",
            "date": (today + timedelta(days=1, hours=12)).isoformat(),
            "price": 0.0,
            "is_free": True,
            "source": "facebook",
            "source_url": "https://facebook.com/events",
            "image_url": "https://images.unsplash.com/photo-1773570357050-fdece406039c?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA3MDB8MHwxfHNlYXJjaHwxfHxpdGFsaWFuJTIwc3RyZWV0JTIwZm9vZCUyMHZpYnJhbnQlMjBtYXJrZXR8ZW58MHx8fHwxNzc0MTMzMTIwfDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 234,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_roma_003",
            "title": "Mostra Caravaggio - Galleria Borghese",
            "description": "Esposizione esclusiva delle opere meno conosciute del Caravaggio.",
            "ai_description": "Un viaggio nell'anima oscura del Caravaggio. Opere rare e restauri inediti ti aspettano nella cornice della Galleria Borghese.",
            "category": "art",
            "lat": 41.9142,
            "lng": 12.4921,
            "address": "Piazzale Scipione Borghese 5",
            "city": "Roma",
            "date": (today + timedelta(days=2, hours=10)).isoformat(),
            "price": 18.0,
            "is_free": False,
            "source": "eventbrite",
            "image_url": "https://images.unsplash.com/photo-1763046224157-4095b90a87cf?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NTYxOTJ8MHwxfHNlYXJjaHwzfHxhcnQlMjBnYWxsZXJ5JTIwZXhoaWJpdGlvbiUyMG1vZGVybiUyMGNvbG9yZnVsfGVufDB8fHx8MTc3NDEzMzExN3ww&ixlib=rb-4.1.0&q=85",
            "participants_count": 89,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_roma_004",
            "title": "Yoga all'Alba al Colosseo",
            "description": "Sessione di yoga con vista sul Colosseo. Porta il tuo tappetino!",
            "ai_description": "Saluta il sole con vista sul monumento più iconico del mondo. Yoga rigenerante all'alba, un'esperienza che ricorderai per sempre.",
            "category": "outdoor",
            "lat": 41.8902,
            "lng": 12.4922,
            "address": "Parco del Colle Oppio",
            "city": "Roma",
            "date": (today + timedelta(days=1, hours=6)).isoformat(),
            "price": 0.0,
            "is_free": True,
            "source": "meetup",
            "image_url": "https://images.unsplash.com/photo-1765607078219-a3891acd43b5?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA2MjJ8MHwxfHNlYXJjaHw0fHxwZW9wbGUlMjBwbGF5aW5nJTIwc3BvcnRzJTIwb3V0ZG9vcnMlMjBlbmVyZ2V0aWN8ZW58MHx8fHwxNzc0MTMzMTE4fDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 28,
            "created_at": now.isoformat()
        },
        # Milano
        {
            "event_id": "evt_milano_001",
            "title": "Aperitivo Tech - Startup Networking",
            "description": "Incontra founder e developer della scena tech milanese. Drink e networking.",
            "ai_description": "Connettiti con la community tech di Milano. Founder, developer e innovatori si incontrano per un aperitivo ricco di opportunità.",
            "category": "networking",
            "lat": 45.4642,
            "lng": 9.1900,
            "address": "Via Tortona 33",
            "city": "Milano",
            "date": (today + timedelta(hours=18, minutes=30)).isoformat(),
            "price": 10.0,
            "is_free": False,
            "source": "meetup",
            "image_url": "https://images.unsplash.com/photo-1759358342214-c23ed81014bc?crop=entropy&cs=srgb&fm=jpg&ixid=M3w3NTY2Njd8MHwxfHNlYXJjaHw0fHxjb2NrdGFpbCUyMGJhciUyMGF0bW9zcGhlcmUlMjBuaWdodGxpZmV8ZW58MHx8fHwxNzc0MTMzMTIxfDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 156,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_milano_002",
            "title": "Partita Calcetto - Parco Sempione",
            "description": "Partita amichevole 5vs5. Tutti i livelli benvenuti!",
            "ai_description": "Sfida i tuoi amici o fai nuove conoscenze con una partita di calcetto al Sempione. Divertimento garantito, skill opzionale!",
            "category": "sport",
            "lat": 45.4734,
            "lng": 9.1765,
            "address": "Parco Sempione",
            "city": "Milano",
            "date": (today + timedelta(days=2, hours=17)).isoformat(),
            "price": 0.0,
            "is_free": True,
            "source": "whatsup",
            "image_url": "https://images.unsplash.com/photo-1765607078219-a3891acd43b5?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA2MjJ8MHwxfHNlYXJjaHw0fHxwZW9wbGUlMjBwbGF5aW5nJTIwc3BvcnRzJTIwb3V0ZG9vcnMlMjBlbmVyZ2V0aWN8ZW58MHx8fHwxNzc0MTMzMTE4fDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 18,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_milano_003",
            "title": "Notte Bianca ai Navigli",
            "description": "Musica live, aperitivi e negozi aperti fino a tardi. L'evento dell'estate milanese.",
            "ai_description": "I Navigli si accendono per una notte magica. Musica, drink e shopping sotto le stelle della Milano più autentica.",
            "category": "nightlife",
            "lat": 45.4494,
            "lng": 9.1726,
            "address": "Naviglio Grande",
            "city": "Milano",
            "date": (today + timedelta(days=3, hours=21)).isoformat(),
            "price": 0.0,
            "is_free": True,
            "source": "facebook",
            "image_url": "https://images.unsplash.com/photo-1759358342214-c23ed81014bc?crop=entropy&cs=srgb&fm=jpg&ixid=M3w3NTY2Njd8MHwxfHNlYXJjaHw0fHxjb2NrdGFpbCUyMGJhciUyMGF0bW9zcGhlcmUlMjBuaWdodGxpZmV8ZW58MHx8fHwxNzc0MTMzMTIxfDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 1250,
            "created_at": now.isoformat()
        },
        # Firenze
        {
            "event_id": "evt_firenze_001",
            "title": "Teatro all'aperto - Divina Commedia",
            "description": "Spettacolo teatrale sotto le stelle. Dante come non l'avete mai visto.",
            "ai_description": "Lasciati trasportare nei gironi danteschi con uno spettacolo teatrale mozzafiato sotto il cielo di Firenze. Emozioni garantite.",
            "category": "teatro",
            "lat": 43.7696,
            "lng": 11.2558,
            "address": "Giardino di Boboli",
            "city": "Firenze",
            "date": (today + timedelta(days=4, hours=21, minutes=30)).isoformat(),
            "price": 25.0,
            "is_free": False,
            "source": "eventbrite",
            "image_url": "https://images.unsplash.com/photo-1763046224157-4095b90a87cf?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NTYxOTJ8MHwxfHNlYXJjaHwzfHxhcnQlMjBnYWxsZXJ5JTIwZXhoaWJpdGlvbiUyMG1vZGVybiUyMGNvbG9yZnVsfGVufDB8fHx8MTc3NDEzMzExN3ww&ixlib=rb-4.1.0&q=85",
            "participants_count": 320,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_firenze_002",
            "title": "Laboratorio di Ceramica per Bambini",
            "description": "Attività creativa per i più piccoli. Materiali inclusi, età 5-12 anni.",
            "ai_description": "Piccoli artisti crescono! Un pomeriggio di creatività dove i bambini creano la loro prima opera in ceramica.",
            "category": "famiglia",
            "lat": 43.7711,
            "lng": 11.2486,
            "address": "Via dei Servi 38",
            "city": "Firenze",
            "date": (today + timedelta(days=1, hours=15)).isoformat(),
            "price": 12.0,
            "is_free": False,
            "source": "whatsup",
            "image_url": "https://images.unsplash.com/photo-1763046224157-4095b90a87cf?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NTYxOTJ8MHwxfHNlYXJjaHwzfHxhcnQlMjBnYWxsZXJ5JTIwZXhoaWJpdGlvbiUyMG1vZGVybiUyMGNvbG9yZnVsfGVufDB8fHx8MTc3NDEzMzExN3ww&ixlib=rb-4.1.0&q=85",
            "participants_count": 15,
            "created_at": now.isoformat()
        },
        # Napoli
        {
            "event_id": "evt_napoli_001",
            "title": "Concerto Indie al Maschio Angioino",
            "description": "I migliori artisti indie italiani in una location unica.",
            "ai_description": "La musica indie conquista il castello. Una serata di suoni e vibrazioni nella cornice storica del Maschio Angioino.",
            "category": "music",
            "lat": 40.8383,
            "lng": 14.2527,
            "address": "Via Vittorio Emanuele III",
            "city": "Napoli",
            "date": (today + timedelta(days=5, hours=20)).isoformat(),
            "price": 20.0,
            "is_free": False,
            "source": "eventbrite",
            "image_url": "https://images.unsplash.com/photo-1768405031181-33bb016e2fbb?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjAzMzl8MHwxfHNlYXJjaHwzfHxjb25jZXJ0JTIwY3Jvd2QlMjBlbmVyZ3klMjBuaWdodCUyMGxpZ2h0fGVufDB8fHx8MTc3NDEzMzExNnww&ixlib=rb-4.1.0&q=85",
            "participants_count": 450,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_napoli_002",
            "title": "Tour Gastronomico Quartieri Spagnoli",
            "description": "Scopri i sapori autentici di Napoli con una guida locale.",
            "ai_description": "Perditi nei vicoli più veri di Napoli assaggiando friggitorie, pizzerie e pasticcerie che solo i locali conoscono.",
            "category": "food",
            "lat": 40.8449,
            "lng": 14.2477,
            "address": "Via Toledo",
            "city": "Napoli",
            "date": (today + timedelta(hours=11)).isoformat(),
            "price": 35.0,
            "is_free": False,
            "source": "meetup",
            "image_url": "https://images.unsplash.com/photo-1773570357050-fdece406039c?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA3MDB8MHwxfHNlYXJjaHwxfHxpdGFsaWFuJTIwc3RyZWV0JTIwZm9vZCUyMHZpYnJhbnQlMjBtYXJrZXR8ZW58MHx8fHwxNzc0MTMzMTIwfDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 12,
            "created_at": now.isoformat()
        },
        # Torino
        {
            "event_id": "evt_torino_001",
            "title": "Degustazione Vini Piemontesi",
            "description": "Barolo, Barbaresco e altri tesori del Piemonte. Con sommelier certificato.",
            "ai_description": "Un viaggio sensoriale tra i migliori vini piemontesi guidato da un sommelier esperto. Impara, degusta, goditi.",
            "category": "food",
            "lat": 45.0703,
            "lng": 7.6869,
            "address": "Eataly Lingotto",
            "city": "Torino",
            "date": (today + timedelta(days=2, hours=19)).isoformat(),
            "price": 45.0,
            "is_free": False,
            "source": "eventbrite",
            "image_url": "https://images.unsplash.com/photo-1773570357050-fdece406039c?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA3MDB8MHwxfHNlYXJjaHwxfHxpdGFsaWFuJTIwc3RyZWV0JTIwZm9vZCUyMHZpYnJhbnQlMjBtYXJrZXR8ZW58MHx8fHwxNzc0MTMzMTIwfDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 24,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_torino_002",
            "title": "Corsa Notturna - Parco Valentino",
            "description": "5km notturni con partenza dal Castello del Valentino. Pettorale illuminato incluso.",
            "ai_description": "Corri nella notte torinese con centinaia di runner. Un'esperienza unica lungo il Po illuminato da mille lucine.",
            "category": "sport",
            "lat": 45.0522,
            "lng": 7.6867,
            "address": "Parco del Valentino",
            "city": "Torino",
            "date": (today + timedelta(days=6, hours=22)).isoformat(),
            "price": 15.0,
            "is_free": False,
            "source": "whatsup",
            "image_url": "https://images.unsplash.com/photo-1765607078219-a3891acd43b5?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA2MjJ8MHwxfHNlYXJjaHw0fHxwZW9wbGUlMjBwbGF5aW5nJTIwc3BvcnRzJTIwb3V0ZG9vcnMlMjBlbmVyZ2V0aWN8ZW58MHx8fHwxNzc0MTMzMTE4fDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 380,
            "created_at": now.isoformat()
        },
        # Bologna
        {
            "event_id": "evt_bologna_001",
            "title": "Aperitivo sui Tetti - Torre Asinelli",
            "description": "Vista esclusiva sulla città dalle torri. Solo 30 posti disponibili.",
            "ai_description": "Bologna ai tuoi piedi. Un aperitivo esclusivo con vista a 360° dalla torre più iconica della città.",
            "category": "nightlife",
            "lat": 44.4942,
            "lng": 11.3464,
            "address": "Piazza di Porta Ravegnana",
            "city": "Bologna",
            "date": (today + timedelta(hours=19, minutes=30)).isoformat(),
            "price": 55.0,
            "is_free": False,
            "source": "eventbrite",
            "image_url": "https://images.unsplash.com/photo-1759358342214-c23ed81014bc?crop=entropy&cs=srgb&fm=jpg&ixid=M3w3NTY2Njd8MHwxfHNlYXJjaHw0fHxjb2NrdGFpbCUyMGJhciUyMGF0bW9zcGhlcmUlMjBuaWdodGxpZmV8ZW58MHx8fHwxNzc0MTMzMTIxfDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 28,
            "created_at": now.isoformat()
        },
        # Venezia
        {
            "event_id": "evt_venezia_001",
            "title": "Regata Storica - Tribuna VIP",
            "description": "Vivi la regata da una posizione privilegiata sul Canal Grande.",
            "ai_description": "Le gondole sfidano le onde del Canal Grande. Tribuna esclusiva per vivere la tradizione veneziana più autentica.",
            "category": "outdoor",
            "lat": 45.4371,
            "lng": 12.3326,
            "address": "Canal Grande",
            "city": "Venezia",
            "date": (today + timedelta(days=7, hours=16)).isoformat(),
            "price": 75.0,
            "is_free": False,
            "source": "eventbrite",
            "image_url": "https://images.unsplash.com/photo-1765607078219-a3891acd43b5?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA2MjJ8MHwxfHNlYXJjaHw0fHxwZW9wbGUlMjBwbGF5aW5nJTIwc3BvcnRzJTIwb3V0ZG9vcnMlMjBlbmVyZ2V0aWN8ZW58MHx8fHwxNzc0MTMzMTE4fDA&ixlib=rb-4.1.0&q=85",
            "participants_count": 120,
            "created_at": now.isoformat()
        },
        {
            "event_id": "evt_venezia_002",
            "title": "Maschere Veneziane - Workshop",
            "description": "Crea la tua maschera veneziana con un artigiano locale.",
            "ai_description": "Diventa artigiano per un giorno. Crea la tua maschera veneziana seguendo tecniche tramandate da generazioni.",
            "category": "art",
            "lat": 45.4408,
            "lng": 12.3155,
            "address": "Dorsoduro 3215",
            "city": "Venezia",
            "date": (today + timedelta(days=3, hours=14)).isoformat(),
            "price": 65.0,
            "is_free": False,
            "source": "meetup",
            "image_url": "https://images.unsplash.com/photo-1763046224157-4095b90a87cf?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NTYxOTJ8MHwxfHNlYXJjaHwzfHxhcnQlMjBnYWxsZXJ5JTIwZXhoaWJpdGlvbiUyMG1vZGVybiUyMGNvbG9yZnVsfGVufDB8fHx8MTc3NDEzMzExN3ww&ixlib=rb-4.1.0&q=85",
            "participants_count": 8,
            "created_at": now.isoformat()
        }
    ]
    
    await db.events.insert_many(mock_events)
    
    return {"message": f"Inseriti {len(mock_events)} eventi mock", "count": len(mock_events)}

# ============ HEALTH CHECK ============

@api_router.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "timestamp": datetime.now(timezone.utc).isoformat()}

# ============ DISCOVERY ENDPOINTS ============

# Store job status in memory (in production use Redis)
discovery_job_status = {
    "status": "idle",
    "last_run": None,
    "result": None
}

@api_router.post("/jobs/discovery/run")
async def run_discovery_job(request: Request):
    """Manually start a discovery run"""
    global discovery_job_status
    
    body = await request.json() if request.headers.get("content-type") == "application/json" else {}
    cities = body.get("cities", ["Roma", "Milano"])
    max_queries = body.get("max_queries", 5)
    max_pages = body.get("max_pages", 10)
    categories = body.get("categories")
    
    # Support for lat/lng based discovery
    lat = body.get("lat")
    lng = body.get("lng")
    
    # If lat/lng provided, find nearest city
    if lat and lng:
        # Find nearest Italian city from coordinates
        from discovery_service import ITALIAN_CITIES
        nearest_city = None
        min_distance = float('inf')
        for city_name in ITALIAN_CITIES:
            # Get city coords (we'll use a simple lookup)
            city_coords = {
                "Milano": (45.4642, 9.1900),
                "Roma": (41.9028, 12.4964),
                "Torino": (45.0703, 7.6869),
                "Napoli": (40.8518, 14.2681),
                "Bologna": (44.4949, 11.3426),
                "Firenze": (43.7696, 11.2558),
                "Venezia": (45.4408, 12.3155),
                "Genova": (44.4056, 8.9463),
                "Palermo": (38.1157, 13.3615),
                "Bari": (41.1171, 16.8719),
                "Verona": (45.4384, 10.9916),
                "Padova": (45.4064, 11.8768),
                "Trieste": (45.6495, 13.7768),
                "Catania": (37.5079, 15.0830),
                "Cagliari": (39.2238, 9.1217),
            }
            if city_name in city_coords:
                c_lat, c_lng = city_coords[city_name]
                dist = haversine_distance(lat, lng, c_lat, c_lng)
                if dist < min_distance:
                    min_distance = dist
                    nearest_city = city_name
        
        if nearest_city:
            cities = [nearest_city]
            logger.info(f"GPS Discovery: Using nearest city {nearest_city} (distance: {min_distance:.1f}km)")
    
    # Check if already running
    if discovery_job_status["status"] == "running":
        raise HTTPException(status_code=409, detail="Discovery job già in esecuzione")
    
    discovery_job_status["status"] = "running"
    discovery_job_status["last_run"] = datetime.now(timezone.utc).isoformat()
    
    try:
        from discovery_service import DiscoveryService
        
        serpapi_key = os.environ.get("SERPAPI_KEY")
        google_maps_key = os.environ.get("GOOGLE_MAPS_API_KEY")
        llm_key = os.environ.get("EMERGENT_LLM_KEY")
        
        if not serpapi_key:
            raise HTTPException(status_code=500, detail="SERPAPI_KEY non configurata")
        
        service = DiscoveryService(serpapi_key, google_maps_key, llm_key)
        result = await service.run_discovery(
            cities=cities,
            max_queries=max_queries,
            max_pages=max_pages,
            categories=categories
        )
        
        # Save discovered events to database
        events_saved = 0
        for event_data in result.get("events", []):
            # Check for duplicates
            existing = await db.discovered_events.find_one(
                {"dedup_hash": event_data["dedup_hash"]},
                {"_id": 0}
            )
            
            if not existing:
                event_data["created_at"] = datetime.now(timezone.utc).isoformat()
                await db.discovered_events.insert_one(event_data)
                events_saved += 1
        
        result["events_saved_to_db"] = events_saved
        discovery_job_status["status"] = "completed"
        
        # Clean result for JSON serialization
        clean_result = {
            "started_at": result.get("started_at"),
            "completed_at": result.get("completed_at"),
            "cities": result.get("cities", []),
            "total_queries": result.get("total_queries", 0),
            "total_pages_fetched": result.get("total_pages_fetched", 0),
            "events_discovered": result.get("events_discovered", 0),
            "events_published": result.get("events_published", 0),
            "events_review": result.get("events_review", 0),
            "events_discarded": result.get("events_discarded", 0),
            "events_saved_to_db": events_saved,
            "errors": result.get("errors", [])
        }
        discovery_job_status["result"] = clean_result
        
        return clean_result
        
    except Exception as e:
        logger.error(f"Discovery job error: {e}")
        discovery_job_status["status"] = "failed"
        discovery_job_status["result"] = {"error": str(e)}
        raise HTTPException(status_code=500, detail=str(e))

@api_router.get("/jobs/discovery/status")
async def get_discovery_status():
    """Get the status of the last discovery job"""
    return discovery_job_status

@api_router.get("/discovery/review-queue")
async def get_review_queue(city: Optional[str] = None, category: Optional[str] = None, limit: int = 50):
    """Get events waiting for manual review"""
    query = {"status": "review"}
    
    if city:
        query["citta"] = city
    if category:
        query["categoria"] = category
    
    events = await db.discovered_events.find(query, {"_id": 0}).limit(limit).to_list(limit)
    return {"events": events, "count": len(events)}

@api_router.get("/discovery/events")
async def get_discovered_events(
    status: Optional[str] = None,
    city: Optional[str] = None,
    limit: int = 50
):
    """Get all discovered events"""
    query = {}
    if status:
        query["status"] = status
    if city:
        query["citta"] = city
    
    events = await db.discovered_events.find(query, {"_id": 0}).sort("created_at", -1).limit(limit).to_list(limit)
    return {"events": events, "count": len(events)}

@api_router.patch("/discovery/events/{dedup_hash}/approve")
async def approve_event(dedup_hash: str):
    """Approve and publish an event from review queue"""
    # Get the discovered event
    discovered = await db.discovered_events.find_one(
        {"dedup_hash": dedup_hash},
        {"_id": 0}
    )
    
    if not discovered:
        raise HTTPException(status_code=404, detail="Evento non trovato")
    
    # Update status
    await db.discovered_events.update_one(
        {"dedup_hash": dedup_hash},
        {"$set": {"status": "published"}}
    )
    
    # Create event in main events collection
    if discovered.get("titolo") and discovered.get("data_iso"):
        # Parse date
        try:
            event_date = datetime.fromisoformat(discovered["data_iso"])
        except:
            event_date = datetime.now(timezone.utc)
        
        # Add time if available
        if discovered.get("ora_inizio"):
            try:
                time_parts = discovered["ora_inizio"].split(":")
                event_date = event_date.replace(
                    hour=int(time_parts[0]),
                    minute=int(time_parts[1]) if len(time_parts) > 1 else 0
                )
            except:
                pass
        
        new_event = {
            "event_id": f"evt_{uuid.uuid4().hex[:12]}",
            "title": discovered["titolo"],
            "description": discovered.get("descrizione_breve", ""),
            "ai_description": discovered.get("descrizione_breve"),
            "category": discovered.get("categoria", "altro"),
            "lat": discovered.get("lat") or 41.9028,  # Default to Rome
            "lng": discovered.get("lng") or 12.4964,
            "address": discovered.get("indirizzo") or discovered.get("luogo_nome") or "",
            "city": discovered.get("citta", ""),
            "date": event_date.isoformat(),
            "price": 0.0,
            "is_free": not discovered.get("prezzo"),
            "source": discovered.get("source_platform", "discovery"),
            "source_url": discovered.get("source_url"),
            "image_url": discovered.get("image_url"),
            "participants_count": 0,
            "created_at": datetime.now(timezone.utc).isoformat()
        }
        
        await db.events.insert_one(new_event)
    
    return {"message": "Evento approvato e pubblicato", "dedup_hash": dedup_hash}

@api_router.patch("/discovery/events/{dedup_hash}/discard")
async def discard_event(dedup_hash: str):
    """Discard an event from review queue"""
    result = await db.discovered_events.update_one(
        {"dedup_hash": dedup_hash},
        {"$set": {"status": "discarded"}}
    )
    
    if result.modified_count == 0:
        raise HTTPException(status_code=404, detail="Evento non trovato")
    
    return {"message": "Evento scartato", "dedup_hash": dedup_hash}

@api_router.get("/discovery/dedup-check")
async def check_duplicate(title: str, date: str, city: str):
    """Check if a similar event already exists"""
    import hashlib
    import re
    
    # Generate hash
    normalized_title = re.sub(r'[^\w\s]', '', title.lower())
    normalized_title = " ".join(normalized_title.split())
    hash_input = f"{normalized_title}|{date}|{city.lower()}"
    dedup_hash = hashlib.sha256(hash_input.encode()).hexdigest()[:16]
    
    existing = await db.discovered_events.find_one(
        {"dedup_hash": dedup_hash},
        {"_id": 0}
    )
    
    return {
        "exists": existing is not None,
        "dedup_hash": dedup_hash,
        "existing_event": existing
    }

# ============ ADMIN ENDPOINTS ============

@api_router.get("/admin/stats")
async def get_admin_stats():
    """Get admin dashboard statistics"""
    total_events = await db.events.count_documents({})
    discovered_total = await db.discovered_events.count_documents({})
    discovered_published = await db.discovered_events.count_documents({"status": "published"})
    discovered_review = await db.discovered_events.count_documents({"status": "review"})
    discovered_discarded = await db.discovered_events.count_documents({"status": "discarded"})
    total_users = await db.users.count_documents({})
    total_rsvps = await db.rsvps.count_documents({"status": "confirmed"})
    
    # Get events by city
    pipeline = [
        {"$group": {"_id": "$city", "count": {"$sum": 1}}},
        {"$sort": {"count": -1}},
        {"$limit": 10}
    ]
    events_by_city = await db.events.aggregate(pipeline).to_list(10)
    
    # Get discovered events by city
    discovered_by_city = await db.discovered_events.aggregate(pipeline).to_list(10)
    
    return {
        "total_events": total_events,
        "discovered": {
            "total": discovered_total,
            "published": discovered_published,
            "review": discovered_review,
            "discarded": discovered_discarded
        },
        "total_users": total_users,
        "total_rsvps": total_rsvps,
        "events_by_city": events_by_city,
        "discovered_by_city": discovered_by_city,
        "last_discovery_job": discovery_job_status
    }

@api_router.get("/admin/cities")
async def get_configured_cities():
    """Get list of configured cities for discovery"""
    from discovery_service import ITALIAN_CITIES, VENUE_WHITELIST
    
    return {
        "cities": ITALIAN_CITIES,
        "venue_whitelist": VENUE_WHITELIST
    }

# Include the router in the main app
app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()
