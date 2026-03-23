"""
Google Proxy Event Discovery Service
Discovers events from Instagram/Facebook via Google Search indexing
"""

import os
import re
import json
import hashlib
import asyncio
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Optional, Any
from dataclasses import dataclass, asdict
from enum import Enum

import httpx
from bs4 import BeautifulSoup
from serpapi import GoogleSearch

logger = logging.getLogger(__name__)

# Event categories enum
class EventCategory(str, Enum):
    CONCERTO = "concerto"
    APERITIVO = "aperitivo"
    SPORT = "sport"
    ARTE = "arte"
    TEATRO = "teatro"
    FOOD = "food"
    NETWORKING = "networking"
    OUTDOOR = "outdoor"
    FAMIGLIA = "famiglia"
    ALTRO = "altro"

# Map to existing app categories
CATEGORY_MAP = {
    "concerto": "music",
    "aperitivo": "nightlife",
    "sport": "sport",
    "arte": "art",
    "teatro": "teatro",
    "food": "food",
    "networking": "networking",
    "outdoor": "outdoor",
    "famiglia": "famiglia",
    "altro": "altro"
}

@dataclass
class DiscoveredEvent:
    titolo: Optional[str]
    data_iso: Optional[str]
    ora_inizio: Optional[str]
    ora_fine: Optional[str]
    luogo_nome: Optional[str]
    indirizzo: Optional[str]
    citta: str
    categoria: str
    prezzo: Optional[str]
    descrizione_breve: str
    source_url: str
    source_platform: str
    confidence_data: float
    confidence_luogo: float
    confidence_categoria: float
    lat: Optional[float] = None
    lng: Optional[float] = None
    image_url: Optional[str] = None
    dedup_hash: Optional[str] = None
    status: str = "published"

# Italian cities configuration
ITALIAN_CITIES = [
    "Milano", "Roma", "Torino", "Napoli", "Bologna", 
    "Firenze", "Venezia", "Genova", "Palermo", "Bari",
    "Verona", "Padova", "Trieste", "Catania", "Cagliari"
]

# Whitelist of known venues/promoters per city
VENUE_WHITELIST = {
    "Milano": ["@alcatraz", "@circolomagnolia", "@fabrique_milano", "@santeria_milano"],
    "Roma": ["@atlantico_roma", "@auditorium_parco_della_musica", "@monk_roma"],
    "Torino": ["@hiroshimamonami", "@jazz_club_torino"],
    "Napoli": ["@duel_beat", "@teatro_augusteo"],
    "Bologna": ["@estragon_club", "@locomotiv_club"],
    "Firenze": ["@tenax_firenze", "@viper_theatre"],
}


class QueryBuilder:
    """Layer 1: Constructs targeted Google search queries"""
    
    @staticmethod
    def build_queries(city: str, time_window: str = "this_week", categories: List[str] = None) -> List[str]:
        """Build search queries for a city"""
        queries = []
        
        # Get current date info
        now = datetime.now()
        month_it = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
                    "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"][now.month - 1]
        year = now.year
        
        # Time window keywords
        time_keywords = {
            "this_week": f'("venerdì" OR "sabato" OR "domenica")',
            "this_month": f'"{month_it}" "{year}"',
            "next_30_days": f'"{year}"'
        }
        time_kw = time_keywords.get(time_window, time_keywords["this_week"])
        
        # Category keywords
        category_keywords = {
            "concerto": '("concerto" OR "live" OR "musica dal vivo")',
            "aperitivo": '("aperitivo" OR "happy hour" OR "dj set")',
            "sport": '("partita" OR "torneo" OR "gara")',
            "arte": '("mostra" OR "esposizione" OR "galleria")',
            "teatro": '("spettacolo" OR "teatro" OR "commedia")',
            "food": '("degustazione" OR "food" OR "cena")',
            "networking": '("networking" OR "meetup" OR "business")',
            "outdoor": '("escursione" OR "trekking" OR "outdoor")',
            "famiglia": '("bambini" OR "famiglia" OR "kids")',
        }
        
        cats = categories or list(category_keywords.keys())
        
        # Instagram queries
        for cat in cats:
            if cat in category_keywords:
                cat_kw = category_keywords[cat]
                # General search
                queries.append(f'site:instagram.com "{city}" {time_kw} {cat_kw} -reel -story')
        
        # Facebook Events queries
        queries.append(f'site:facebook.com/events "{city}" {month_it} {year}')
        
        # Venue-specific queries
        venues = VENUE_WHITELIST.get(city, [])
        if venues:
            venue_str = " OR ".join([f'"{v}"' for v in venues[:3]])  # Limit to 3 venues
            queries.append(f'site:instagram.com ({venue_str}) {time_kw}')
        
        return queries


class FetchParser:
    """Layer 2: Fetch and parse content from URLs"""
    
    def __init__(self, serpapi_key: str):
        self.serpapi_key = serpapi_key
        self.http_client = None
    
    async def __aenter__(self):
        self.http_client = httpx.AsyncClient(timeout=8.0, follow_redirects=True)
        return self
    
    async def __aexit__(self, *args):
        if self.http_client:
            await self.http_client.aclose()
    
    def search_google(self, query: str, num_results: int = 10) -> List[Dict]:
        """Execute Google search via SerpAPI"""
        try:
            search = GoogleSearch({
                "q": query,
                "api_key": self.serpapi_key,
                "num": num_results,
                "gl": "it",
                "hl": "it"
            })
            results = search.get_dict()
            return results.get("organic_results", [])
        except Exception as e:
            logger.error(f"SerpAPI error: {e}")
            return []
    
    async def fetch_page_content(self, url: str) -> Dict[str, Any]:
        """Fetch and parse a public page"""
        result = {
            "url": url,
            "title": None,
            "description": None,
            "caption": None,
            "hashtags": [],
            "image_url": None,
            "platform": self._detect_platform(url)
        }
        
        try:
            response = await self.http_client.get(url, headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            })
            
            if response.status_code != 200:
                return result
            
            soup = BeautifulSoup(response.text, 'html.parser')
            
            # Extract OpenGraph data
            og_title = soup.find("meta", property="og:title")
            og_desc = soup.find("meta", property="og:description")
            og_image = soup.find("meta", property="og:image")
            
            result["title"] = og_title["content"] if og_title else None
            result["description"] = og_desc["content"] if og_desc else None
            result["image_url"] = og_image["content"] if og_image else None
            
            # Extract caption text (Instagram-specific)
            if "instagram.com" in url:
                # Try to find caption in meta description
                meta_desc = soup.find("meta", attrs={"name": "description"})
                if meta_desc:
                    result["caption"] = meta_desc.get("content", "")
            
            # Extract hashtags
            text = result.get("caption") or result.get("description") or ""
            result["hashtags"] = re.findall(r'#(\w+)', text)
            
        except Exception as e:
            logger.error(f"Fetch error for {url}: {e}")
        
        return result
    
    def _detect_platform(self, url: str) -> str:
        if "instagram.com" in url:
            return "instagram"
        elif "facebook.com" in url:
            return "facebook"
        elif "eventbrite" in url:
            return "eventbrite"
        return "other"


class AIExtractor:
    """Layer 3: Extract structured event data using LLM"""
    
    SYSTEM_PROMPT = """Sei un estrattore strutturato di eventi da testo italiano. Rispondi SEMPRE e SOLO con un oggetto JSON valido. Nessun testo prima o dopo. Se un campo non è rilevabile con certezza, usa null. Il campo 'descrizione_breve' deve essere max 120 caratteri, tono neutro, in italiano. Il campo 'categoria' deve essere uno di: concerto, aperitivo, sport, arte, teatro, food, networking, outdoor, famiglia, altro. I confidence score vanno da 0.0 (nessuna certezza) a 1.0 (certezza assoluta).

Schema JSON richiesto:
{
  "titolo": string | null,
  "data_iso": string | null,
  "ora_inizio": string | null,
  "ora_fine": string | null,
  "luogo_nome": string | null,
  "indirizzo": string | null,
  "citta": string,
  "categoria": string,
  "prezzo": string | null,
  "descrizione_breve": string,
  "confidence": {
    "data": number,
    "luogo": number,
    "categoria": number
  }
}"""

    def __init__(self, api_key: str):
        self.api_key = api_key
    
    async def extract_event(self, content: Dict, city: str) -> Optional[Dict]:
        """Extract event data from content using Claude"""
        try:
            from emergentintegrations.llm.chat import LlmChat, UserMessage
            
            # Build context text
            text_parts = []
            if content.get("title"):
                text_parts.append(f"TITOLO: {content['title']}")
            if content.get("description"):
                text_parts.append(f"DESCRIZIONE: {content['description']}")
            if content.get("caption"):
                text_parts.append(f"CAPTION: {content['caption']}")
            
            if not text_parts:
                return None
            
            text = "\n".join(text_parts)
            hashtags = ", ".join(content.get("hashtags", [])) or "nessuno"
            today = datetime.now().strftime("%Y-%m-%d")
            
            user_prompt = f"""Estrai i dati evento dal seguente testo:
FONTE: {content['url']}
TESTO: {text}
HASHTAG RILEVATI: {hashtags}
CITTA' CONTESTO: {city}
DATA OGGI: {today}"""

            chat = LlmChat(
                api_key=self.api_key,
                session_id=f"extract_{hashlib.md5(content['url'].encode()).hexdigest()[:8]}",
                system_message=self.SYSTEM_PROMPT
            ).with_model("anthropic", "claude-sonnet-4-5-20250929")
            
            response = await chat.send_message(UserMessage(text=user_prompt))
            
            # Parse JSON response
            # Clean response - remove any markdown code blocks
            clean_response = response.strip()
            if clean_response.startswith("```"):
                clean_response = re.sub(r'^```\w*\n?', '', clean_response)
                clean_response = re.sub(r'\n?```$', '', clean_response)
            
            data = json.loads(clean_response)
            return data
            
        except json.JSONDecodeError as e:
            logger.error(f"JSON parse error: {e}, response: {response[:200] if response else 'None'}")
            return None
        except Exception as e:
            logger.error(f"AI extraction error: {e}")
            return None


class GeocodingValidator:
    """Layer 4: Validation and Geocoding"""
    
    def __init__(self, google_maps_key: str):
        self.maps_key = google_maps_key
        self.http_client = None
    
    async def __aenter__(self):
        self.http_client = httpx.AsyncClient(timeout=10.0)
        return self
    
    async def __aexit__(self, *args):
        if self.http_client:
            await self.http_client.aclose()
    
    async def geocode_venue(self, venue_name: str, city: str, address: str = None) -> Dict:
        """Geocode a venue using Google Maps API"""
        result = {"lat": None, "lng": None, "formatted_address": None, "verified": False}
        
        try:
            # Build query
            query_parts = [venue_name] if venue_name else []
            if address:
                query_parts.append(address)
            query_parts.append(city)
            query_parts.append("Italia")
            query = ", ".join(filter(None, query_parts))
            
            url = "https://maps.googleapis.com/maps/api/geocode/json"
            params = {
                "address": query,
                "key": self.maps_key,
                "language": "it",
                "region": "it"
            }
            
            response = await self.http_client.get(url, params=params)
            data = response.json()
            
            if data.get("status") == "OK" and data.get("results"):
                location = data["results"][0]["geometry"]["location"]
                result["lat"] = location["lat"]
                result["lng"] = location["lng"]
                result["formatted_address"] = data["results"][0].get("formatted_address")
                result["verified"] = True
                
        except Exception as e:
            logger.error(f"Geocoding error: {e}")
        
        return result
    
    @staticmethod
    def generate_dedup_hash(title: str, date: str, city: str) -> str:
        """Generate deduplication hash"""
        # Normalize title
        normalized_title = re.sub(r'[^\w\s]', '', (title or "").lower())
        normalized_title = " ".join(normalized_title.split())
        
        hash_input = f"{normalized_title}|{date or ''}|{city.lower()}"
        return hashlib.sha256(hash_input.encode()).hexdigest()[:16]
    
    @staticmethod
    def validate_event(extracted: Dict, content: Dict, geo_result: Dict, city: str) -> DiscoveredEvent:
        """Validate and create DiscoveredEvent"""
        confidence = extracted.get("confidence", {})
        conf_data = confidence.get("data", 0.5)
        conf_luogo = confidence.get("luogo", 0.5)
        conf_cat = confidence.get("categoria", 0.5)
        
        # Determine status based on confidence
        if conf_data < 0.8 or conf_luogo < 0.8:
            status = "review"
        elif not extracted.get("data_iso"):
            status = "discarded"
        else:
            status = "published"
        
        # Map category
        raw_cat = extracted.get("categoria", "altro")
        mapped_cat = CATEGORY_MAP.get(raw_cat, "altro")
        
        # Generate dedup hash
        dedup_hash = GeocodingValidator.generate_dedup_hash(
            extracted.get("titolo"),
            extracted.get("data_iso"),
            city
        )
        
        return DiscoveredEvent(
            titolo=extracted.get("titolo"),
            data_iso=extracted.get("data_iso"),
            ora_inizio=extracted.get("ora_inizio"),
            ora_fine=extracted.get("ora_fine"),
            luogo_nome=extracted.get("luogo_nome"),
            indirizzo=geo_result.get("formatted_address") or extracted.get("indirizzo"),
            citta=city,
            categoria=mapped_cat,
            prezzo=extracted.get("prezzo"),
            descrizione_breve=extracted.get("descrizione_breve", "")[:120],
            source_url=content["url"],
            source_platform=content.get("platform", "other"),
            confidence_data=conf_data,
            confidence_luogo=conf_luogo,
            confidence_categoria=conf_cat,
            lat=geo_result.get("lat"),
            lng=geo_result.get("lng"),
            image_url=content.get("image_url"),
            dedup_hash=dedup_hash,
            status=status
        )


class DiscoveryService:
    """Main discovery service orchestrator"""
    
    def __init__(self, serpapi_key: str, google_maps_key: str, llm_key: str):
        self.serpapi_key = serpapi_key
        self.google_maps_key = google_maps_key
        self.llm_key = llm_key
        self.query_builder = QueryBuilder()
        self.ai_extractor = AIExtractor(llm_key)
    
    async def run_discovery(
        self, 
        cities: List[str] = None,
        max_queries: int = 10,
        max_pages: int = 20,
        categories: List[str] = None
    ) -> Dict[str, Any]:
        """Run discovery job for specified cities"""
        
        cities = cities or ITALIAN_CITIES[:3]  # Default to first 3 cities
        results = {
            "started_at": datetime.now(timezone.utc).isoformat(),
            "cities": cities,
            "total_queries": 0,
            "total_pages_fetched": 0,
            "events_discovered": 0,
            "events_published": 0,
            "events_review": 0,
            "events_discarded": 0,
            "events": [],
            "errors": []
        }
        
        async with FetchParser(self.serpapi_key) as fetcher:
            async with GeocodingValidator(self.google_maps_key) as geocoder:
                
                for city in cities:
                    logger.info(f"Processing city: {city}")
                    
                    # Build queries
                    queries = self.query_builder.build_queries(city, categories=categories)
                    queries = queries[:max_queries]  # Limit queries
                    results["total_queries"] += len(queries)
                    
                    seen_urls = set()
                    
                    for query in queries:
                        try:
                            # Search Google
                            search_results = fetcher.search_google(query, num_results=5)
                            
                            for sr in search_results:
                                url = sr.get("link", "")
                                
                                # Skip duplicates
                                if url in seen_urls:
                                    continue
                                seen_urls.add(url)
                                
                                if results["total_pages_fetched"] >= max_pages:
                                    break
                                
                                # Fetch page content
                                content = await fetcher.fetch_page_content(url)
                                results["total_pages_fetched"] += 1
                                
                                # Use snippet as fallback
                                if not content.get("description") and sr.get("snippet"):
                                    content["description"] = sr["snippet"]
                                if not content.get("title") and sr.get("title"):
                                    content["title"] = sr["title"]
                                
                                # Extract with AI
                                extracted = await self.ai_extractor.extract_event(content, city)
                                
                                if not extracted:
                                    continue
                                
                                # Geocode
                                geo_result = await geocoder.geocode_venue(
                                    extracted.get("luogo_nome"),
                                    city,
                                    extracted.get("indirizzo")
                                )
                                
                                # Validate and create event
                                event = GeocodingValidator.validate_event(
                                    extracted, content, geo_result, city
                                )
                                
                                results["events_discovered"] += 1
                                
                                if event.status == "published":
                                    results["events_published"] += 1
                                elif event.status == "review":
                                    results["events_review"] += 1
                                else:
                                    results["events_discarded"] += 1
                                
                                results["events"].append(asdict(event))
                                
                                # Rate limiting
                                await asyncio.sleep(1.5)
                                
                        except Exception as e:
                            logger.error(f"Error processing query '{query}': {e}")
                            results["errors"].append(str(e))
                    
                    # Delay between cities
                    await asyncio.sleep(2)
        
        results["completed_at"] = datetime.now(timezone.utc).isoformat()
        return results
