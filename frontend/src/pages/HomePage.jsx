import { useState, useEffect, useMemo, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import L from "leaflet";
import { useAuth } from "@/App";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { 
  Map, 
  List, 
  User, 
  MapPin, 
  Clock, 
  Calendar, 
  Filter,
  Music,
  Palette,
  Utensils,
  PartyPopper,
  Users,
  Theater,
  Trees,
  Baby,
  MoreHorizontal,
  Dumbbell,
  X,
  ChevronDown,
  Navigation,
  Search,
  Check
} from "lucide-react";
import { toast } from "sonner";

// Italian cities with coordinates
const ITALIAN_CITIES = [
  { name: "Roma", lat: 41.9028, lng: 12.4964 },
  { name: "Milano", lat: 45.4642, lng: 9.1900 },
  { name: "Napoli", lat: 40.8518, lng: 14.2681 },
  { name: "Torino", lat: 45.0703, lng: 7.6869 },
  { name: "Firenze", lat: 43.7696, lng: 11.2558 },
  { name: "Bologna", lat: 44.4949, lng: 11.3426 },
  { name: "Venezia", lat: 45.4408, lng: 12.3155 },
  { name: "Genova", lat: 44.4056, lng: 8.9463 },
  { name: "Palermo", lat: 38.1157, lng: 13.3615 },
  { name: "Bari", lat: 41.1171, lng: 16.8719 },
  { name: "Catania", lat: 37.5079, lng: 15.0830 },
  { name: "Verona", lat: 45.4384, lng: 10.9916 },
  { name: "Padova", lat: 45.4064, lng: 11.8768 },
  { name: "Trieste", lat: 45.6495, lng: 13.7768 },
  { name: "Cagliari", lat: 39.2238, lng: 9.1217 },
];

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
const API = `${BACKEND_URL}/api`;

// Category config with icons
const CATEGORIES = {
  music: { name: "Musica", color: "#FF4500", icon: Music },
  sport: { name: "Sport", color: "#00A86B", icon: Dumbbell },
  art: { name: "Arte", color: "#0057B7", icon: Palette },
  food: { name: "Food", color: "#FFD700", icon: Utensils },
  nightlife: { name: "Nightlife", color: "#800080", icon: PartyPopper },
  networking: { name: "Networking", color: "#FF6B33", icon: Users },
  teatro: { name: "Teatro", color: "#D32F2F", icon: Theater },
  outdoor: { name: "Outdoor", color: "#2E8B57", icon: Trees },
  famiglia: { name: "Famiglia", color: "#FF69B4", icon: Baby },
  altro: { name: "Altro", color: "#808080", icon: MoreHorizontal },
};

// Custom marker icon creator
const createMarkerIcon = (category) => {
  const color = CATEGORIES[category]?.color || "#808080";
  return L.divIcon({
    className: "custom-marker",
    html: `<div class="marker-pin marker-${category}" style="background-color: ${color}">
      <span class="marker-icon"></span>
    </div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 32],
    popupAnchor: [0, -32],
  });
};

// Map center updater component
const MapCenterUpdater = ({ center }) => {
  const map = useMap();
  useEffect(() => {
    if (center) {
      map.setView(center, map.getZoom());
    }
  }, [center, map]);
  return null;
};

// Event Card Component
const EventCard = ({ event, onClick }) => {
  const CategoryIcon = CATEGORIES[event.category]?.icon || MoreHorizontal;
  const categoryColor = CATEGORIES[event.category]?.color || "#808080";
  
  const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    const isTomorrow = date.toDateString() === new Date(now.getTime() + 86400000).toDateString();
    
    if (isToday) return `Oggi, ${date.toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" })}`;
    if (isTomorrow) return `Domani, ${date.toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" })}`;
    return date.toLocaleDateString("it-IT", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
  };

  return (
    <Card 
      data-testid={`event-card-${event.event_id}`}
      className="event-card bg-white rounded-2xl overflow-hidden cursor-pointer group border-0 shadow-sm hover:shadow-xl transition-all"
      onClick={() => onClick(event)}
    >
      <div className="relative h-36 overflow-hidden">
        <img 
          src={event.image_url} 
          alt={event.title}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        <div className="absolute top-3 left-3">
          <span 
            className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider text-white"
            style={{ backgroundColor: categoryColor }}
          >
            {CATEGORIES[event.category]?.name || event.category}
          </span>
        </div>
        {event.is_free && (
          <div className="absolute top-3 right-3 bg-green-500 text-white px-2 py-1 rounded-full text-xs font-bold">
            GRATIS
          </div>
        )}
      </div>
      <div className="p-4">
        <h3 className="font-bold text-lg leading-tight mb-2 line-clamp-2">{event.title}</h3>
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
          <Clock className="w-4 h-4" />
          <span>{formatDate(event.date)}</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <MapPin className="w-4 h-4" />
            <span className="truncate max-w-[140px]">{event.city}</span>
          </div>
          {event.distance_km !== null && (
            <span className="font-mono text-sm font-medium text-primary">
              {event.distance_km} km
            </span>
          )}
        </div>
        <div className="mt-3 pt-3 border-t flex items-center justify-between">
          <span className={`source-badge source-${event.source}`}>
            via {event.source}
          </span>
          <span className="text-sm font-medium">
            {event.participants_count} partecipanti
          </span>
        </div>
      </div>
    </Card>
  );
};

// Filter Pills Component
const FilterPills = ({ filters, setFilters }) => {
  const dateOptions = [
    { value: "all", label: "Tutti" },
    { value: "today", label: "Oggi" },
    { value: "tomorrow", label: "Domani" },
    { value: "weekend", label: "Weekend" },
  ];

  const distanceOptions = [
    { value: null, label: "Ovunque" },
    { value: 2, label: "2 km" },
    { value: 5, label: "5 km" },
    { value: 10, label: "10 km" },
  ];

  return (
    <div className="flex flex-wrap gap-2">
      {/* Date Filter */}
      <div className="flex gap-1 p-1 bg-white/80 backdrop-blur-sm rounded-full shadow-sm">
        {dateOptions.map((opt) => (
          <button
            key={opt.value}
            data-testid={`filter-date-${opt.value}`}
            onClick={() => setFilters({ ...filters, dateFilter: opt.value })}
            className={`px-3 py-1.5 text-sm font-medium rounded-full transition-all ${
              filters.dateFilter === opt.value
                ? "bg-primary text-white shadow-md"
                : "hover:bg-muted"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* Distance Filter */}
      <div className="flex gap-1 p-1 bg-white/80 backdrop-blur-sm rounded-full shadow-sm">
        {distanceOptions.map((opt) => (
          <button
            key={opt.value ?? "null"}
            data-testid={`filter-distance-${opt.value ?? "all"}`}
            onClick={() => setFilters({ ...filters, distanceKm: opt.value })}
            className={`px-3 py-1.5 text-sm font-medium rounded-full transition-all ${
              filters.distanceKm === opt.value
                ? "bg-primary text-white shadow-md"
                : "hover:bg-muted"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>
    </div>
  );
};

// Location Picker Sheet Component
const LocationPickerSheet = ({ open, setOpen, currentLocation, currentLocationName, onSelectLocation, onUseGPS }) => {
  const [searchQuery, setSearchQuery] = useState("");
  const [gpsLoading, setGpsLoading] = useState(false);
  
  const filteredCities = ITALIAN_CITIES.filter((city) =>
    city.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleGPS = async () => {
    setGpsLoading(true);
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          onUseGPS([position.coords.latitude, position.coords.longitude]);
          setGpsLoading(false);
          setOpen(false);
          toast.success("Posizione rilevata!");
        },
        (error) => {
          console.log("Geolocation error:", error);
          toast.error("Impossibile rilevare la posizione. Verifica i permessi.");
          setGpsLoading(false);
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    } else {
      toast.error("Geolocalizzazione non supportata");
      setGpsLoading(false);
    }
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="bottom" className="rounded-t-3xl h-[85vh]">
        <div className="sheet-handle" />
        <h3 className="text-xl font-bold mb-4">Scegli la tua posizione</h3>
        
        {/* GPS Button */}
        <Button
          onClick={handleGPS}
          disabled={gpsLoading}
          className="w-full mb-4 h-14 rounded-xl bg-primary hover:bg-primary/90"
          data-testid="use-gps-button"
        >
          <Navigation className={`w-5 h-5 mr-3 ${gpsLoading ? "animate-spin" : ""}`} />
          {gpsLoading ? "Rilevamento in corso..." : "Usa la mia posizione attuale"}
        </Button>
        
        {/* Search Input */}
        <div className="relative mb-4">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
          <Input
            placeholder="Cerca città..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10 h-12 rounded-xl"
            data-testid="city-search-input"
          />
        </div>
        
        {/* Cities List */}
        <ScrollArea className="h-[calc(85vh-220px)]">
          <div className="space-y-2">
            {filteredCities.map((city) => {
              const isSelected = currentLocationName === city.name;
              return (
                <button
                  key={city.name}
                  onClick={() => {
                    onSelectLocation([city.lat, city.lng], city.name);
                    setOpen(false);
                    toast.success(`Posizione impostata: ${city.name}`);
                  }}
                  className={`w-full flex items-center justify-between p-4 rounded-xl transition-all ${
                    isSelected
                      ? "bg-primary/10 border-2 border-primary"
                      : "bg-muted/30 hover:bg-muted/50 border-2 border-transparent"
                  }`}
                  data-testid={`city-${city.name.toLowerCase()}`}
                >
                  <div className="flex items-center gap-3">
                    <MapPin className={`w-5 h-5 ${isSelected ? "text-primary" : "text-muted-foreground"}`} />
                    <span className={`font-medium ${isSelected ? "text-primary" : ""}`}>{city.name}</span>
                  </div>
                  {isSelected && <Check className="w-5 h-5 text-primary" />}
                </button>
              );
            })}
          </div>
        </ScrollArea>
      </SheetContent>
    </Sheet>
  );
};

// Category Filter Sheet
const CategoryFilterSheet = ({ selectedCategories, setSelectedCategories, open, setOpen }) => {
  const toggleCategory = (catId) => {
    if (selectedCategories.includes(catId)) {
      setSelectedCategories(selectedCategories.filter((c) => c !== catId));
    } else {
      setSelectedCategories([...selectedCategories, catId]);
    }
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="bottom" className="rounded-t-3xl">
        <div className="sheet-handle" />
        <h3 className="text-xl font-bold mb-4">Filtra per categoria</h3>
        <div className="grid grid-cols-2 gap-3">
          {Object.entries(CATEGORIES).map(([id, cat]) => {
            const Icon = cat.icon;
            const isSelected = selectedCategories.length === 0 || selectedCategories.includes(id);
            return (
              <button
                key={id}
                data-testid={`category-filter-${id}`}
                onClick={() => toggleCategory(id)}
                className={`flex items-center gap-3 p-3 rounded-xl transition-all ${
                  isSelected
                    ? "bg-primary/10 border-2 border-primary"
                    : "bg-muted/50 border-2 border-transparent opacity-50"
                }`}
              >
                <div
                  className="w-10 h-10 rounded-lg flex items-center justify-center"
                  style={{ backgroundColor: cat.color }}
                >
                  <Icon className="w-5 h-5 text-white" />
                </div>
                <span className="font-medium">{cat.name}</span>
              </button>
            );
          })}
        </div>
        <div className="mt-6 flex gap-3">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => setSelectedCategories([])}
            data-testid="clear-category-filter"
          >
            Tutti
          </Button>
          <Button
            className="flex-1"
            onClick={() => setOpen(false)}
            data-testid="apply-category-filter"
          >
            Applica
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
};

// Main HomePage Component
export default function HomePage() {
  const navigate = useNavigate();
  const { user, login, logout } = useAuth();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState("map"); // map | list
  const [userLocation, setUserLocation] = useState(null);
  const [locationName, setLocationName] = useState(null); // City name or "Posizione attuale"
  const [locationSheetOpen, setLocationSheetOpen] = useState(false);
  const [filters, setFilters] = useState({
    dateFilter: "all",
    distanceKm: null,
  });
  const [selectedCategories, setSelectedCategories] = useState([]);
  const [categorySheetOpen, setCategorySheetOpen] = useState(false);
  
  // Default center: Rome
  const defaultCenter = [41.9028, 12.4964];

  // Get user location on first load
  useEffect(() => {
    // Check if we have a saved location
    const savedLocation = localStorage.getItem("whatsup_location");
    const savedLocationName = localStorage.getItem("whatsup_location_name");
    
    if (savedLocation) {
      const parsed = JSON.parse(savedLocation);
      setUserLocation(parsed);
      setLocationName(savedLocationName || "Posizione salvata");
    } else if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const loc = [position.coords.latitude, position.coords.longitude];
          setUserLocation(loc);
          setLocationName("Posizione attuale");
          localStorage.setItem("whatsup_location", JSON.stringify(loc));
          localStorage.setItem("whatsup_location_name", "Posizione attuale");
        },
        (error) => {
          console.log("Geolocation error:", error);
          // Default to Rome
          setUserLocation(defaultCenter);
          setLocationName("Roma");
          toast.info("Geolocalizzazione non disponibile. Seleziona una città.");
        }
      );
    } else {
      setUserLocation(defaultCenter);
      setLocationName("Roma");
    }
  }, []);

  // Handle location selection from picker
  const handleSelectLocation = (coords, name) => {
    setUserLocation(coords);
    setLocationName(name);
    localStorage.setItem("whatsup_location", JSON.stringify(coords));
    localStorage.setItem("whatsup_location_name", name);
  };

  // Handle GPS location
  const handleUseGPS = (coords) => {
    setUserLocation(coords);
    setLocationName("Posizione attuale");
    localStorage.setItem("whatsup_location", JSON.stringify(coords));
    localStorage.setItem("whatsup_location_name", "Posizione attuale");
  };

  // Fetch events
  const fetchEvents = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      
      if (userLocation) {
        params.append("lat", userLocation[0]);
        params.append("lng", userLocation[1]);
      }
      
      if (filters.distanceKm) {
        params.append("distance_km", filters.distanceKm);
      }
      
      if (filters.dateFilter && filters.dateFilter !== "all") {
        params.append("date_filter", filters.dateFilter);
      }
      
      if (selectedCategories.length > 0) {
        params.append("category", selectedCategories.join(","));
      }

      const response = await fetch(`${API}/events?${params.toString()}`);
      if (response.ok) {
        const data = await response.json();
        setEvents(data);
      }
    } catch (error) {
      console.error("Error fetching events:", error);
      toast.error("Errore nel caricamento degli eventi");
    } finally {
      setLoading(false);
    }
  }, [userLocation, filters, selectedCategories]);

  useEffect(() => {
    fetchEvents();
  }, [fetchEvents]);

  // Seed events on first load
  useEffect(() => {
    const seedEvents = async () => {
      try {
        await fetch(`${API}/seed-events`, { method: "POST" });
      } catch (e) {
        console.log("Events already seeded");
      }
    };
    seedEvents();
  }, []);

  const mapCenter = userLocation || defaultCenter;

  const handleEventClick = (event) => {
    navigate(`/event/${event.event_id}`);
  };

  return (
    <div className="min-h-screen bg-background" data-testid="home-page">
      {/* Mobile Header */}
      <header className="fixed top-0 left-0 right-0 px-4 pt-4 lg:hidden" style={{ zIndex: 1000 }}>
        <div className="glass rounded-2xl p-3 flex items-center justify-between">
          <h1 className="text-xl font-bold text-primary">Whatsup</h1>
          <div className="flex items-center gap-2">
            {user ? (
              <Button
                variant="ghost"
                size="icon"
                onClick={() => navigate("/profile")}
                data-testid="profile-button"
              >
                <User className="w-5 h-5" />
              </Button>
            ) : (
              <Button
                variant="default"
                size="sm"
                onClick={login}
                data-testid="login-button"
              >
                Accedi
              </Button>
            )}
          </div>
        </div>
      </header>

      {/* Desktop Layout */}
      <div className="hidden lg:flex">
        {/* Left Panel - Event List */}
        <div className="split-view-left bg-background border-r">
          <div className="p-6 sticky top-0 bg-background z-10">
            <div className="flex items-center justify-between mb-6">
              <h1 className="text-3xl font-bold text-primary">Whatsup</h1>
              <div className="flex items-center gap-2">
                {user ? (
                  <Button
                    variant="outline"
                    onClick={() => navigate("/profile")}
                    data-testid="desktop-profile-button"
                  >
                    <User className="w-4 h-4 mr-2" />
                    Profilo
                  </Button>
                ) : (
                  <Button onClick={login} data-testid="desktop-login-button">
                    Accedi con Google
                  </Button>
                )}
              </div>
            </div>
            
            {/* Location Picker Button */}
            <button
              onClick={() => setLocationSheetOpen(true)}
              className="w-full mb-4 flex items-center gap-3 px-4 py-3 bg-white rounded-xl shadow-sm hover:shadow-md transition-all border border-border"
              data-testid="desktop-location-picker"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <MapPin className="w-5 h-5 text-primary" />
              </div>
              <div className="flex-1 text-left">
                <p className="text-xs text-muted-foreground">La tua posizione</p>
                <p className="font-medium">{locationName || "Seleziona città"}</p>
              </div>
              <ChevronDown className="w-5 h-5 text-muted-foreground" />
            </button>
            
            <FilterPills filters={filters} setFilters={setFilters} />
            
            <button
              onClick={() => setCategorySheetOpen(true)}
              className="mt-4 flex items-center gap-2 px-4 py-2 bg-white rounded-full shadow-sm hover:shadow-md transition-all"
              data-testid="open-category-filter"
            >
              <Filter className="w-4 h-4" />
              <span className="text-sm font-medium">
                {selectedCategories.length > 0
                  ? `${selectedCategories.length} categorie`
                  : "Tutte le categorie"}
              </span>
              <ChevronDown className="w-4 h-4" />
            </button>
          </div>

          <ScrollArea className="h-[calc(100vh-220px)] px-6 pb-6">
            {loading ? (
              <div className="grid grid-cols-1 gap-4">
                {[1, 2, 3, 4].map((i) => (
                  <div key={i} className="h-64 rounded-2xl skeleton" />
                ))}
              </div>
            ) : events.length === 0 ? (
              <div className="text-center py-12">
                <MapPin className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
                <p className="text-muted-foreground">Nessun evento trovato</p>
                <Button
                  variant="outline"
                  className="mt-4"
                  onClick={() => {
                    setFilters({ dateFilter: "all", distanceKm: null });
                    setSelectedCategories([]);
                  }}
                >
                  Rimuovi filtri
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4">
                {events.map((event, idx) => (
                  <div
                    key={event.event_id}
                    className={`animate-fade-in-up stagger-${(idx % 5) + 1}`}
                  >
                    <EventCard event={event} onClick={handleEventClick} />
                  </div>
                ))}
              </div>
            )}
          </ScrollArea>
        </div>

        {/* Right Panel - Map */}
        <div className="split-view-right">
          <MapContainer
            center={mapCenter}
            zoom={13}
            className="h-full w-full"
            zoomControl={false}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapCenterUpdater center={mapCenter} />
            {events.map((event) => (
              <Marker
                key={event.event_id}
                position={[event.lat, event.lng]}
                icon={createMarkerIcon(event.category)}
                eventHandlers={{
                  click: () => handleEventClick(event),
                }}
              >
                <Popup>
                  <div className="p-2 min-w-[200px]">
                    <h4 className="font-bold text-sm mb-1">{event.title}</h4>
                    <p className="text-xs text-muted-foreground mb-2">{event.city}</p>
                    <Button
                      size="sm"
                      className="w-full"
                      onClick={() => handleEventClick(event)}
                    >
                      Dettagli
                    </Button>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>
      </div>

      {/* Mobile Layout */}
      <div className="lg:hidden">
        {viewMode === "map" ? (
          <div className="h-screen">
            <MapContainer
              center={mapCenter}
              zoom={13}
              className="h-full w-full"
              zoomControl={false}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <MapCenterUpdater center={mapCenter} />
              {events.map((event) => (
                <Marker
                  key={event.event_id}
                  position={[event.lat, event.lng]}
                  icon={createMarkerIcon(event.category)}
                  eventHandlers={{
                    click: () => handleEventClick(event),
                  }}
                >
                  <Popup>
                    <div className="p-2 min-w-[180px]">
                      <h4 className="font-bold text-sm mb-1">{event.title}</h4>
                      <p className="text-xs text-muted-foreground mb-2">{event.city}</p>
                      <Button
                        size="sm"
                        className="w-full"
                        onClick={() => handleEventClick(event)}
                      >
                        Dettagli
                      </Button>
                    </div>
                  </Popup>
                </Marker>
              ))}
            </MapContainer>

            {/* Mobile Filters Overlay */}
            <div className="fixed top-20 left-4 right-4" style={{ zIndex: 1000 }}>
              {/* Location Picker - Mobile */}
              <button
                onClick={() => setLocationSheetOpen(true)}
                className="w-full mb-2 flex items-center gap-2 px-4 py-2.5 bg-white/95 backdrop-blur-sm rounded-xl shadow-md"
                data-testid="mobile-location-picker"
              >
                <MapPin className="w-4 h-4 text-primary" />
                <span className="text-sm font-medium flex-1 text-left truncate">
                  {locationName || "Seleziona posizione"}
                </span>
                <ChevronDown className="w-4 h-4 text-muted-foreground" />
              </button>
              
              <FilterPills filters={filters} setFilters={setFilters} />
              <button
                onClick={() => setCategorySheetOpen(true)}
                className="mt-2 flex items-center gap-2 px-4 py-2 bg-white/90 backdrop-blur-sm rounded-full shadow-md"
                data-testid="mobile-category-filter"
              >
                <Filter className="w-4 h-4" />
                <span className="text-sm font-medium">
                  {selectedCategories.length > 0
                    ? `${selectedCategories.length} categorie`
                    : "Categorie"}
                </span>
              </button>
            </div>
          </div>
        ) : (
          <div className="pt-20 pb-24 px-4">
            <div className="mb-4">
              {/* Location Picker - List View */}
              <button
                onClick={() => setLocationSheetOpen(true)}
                className="w-full mb-3 flex items-center gap-2 px-4 py-2.5 bg-white rounded-xl shadow-sm"
                data-testid="list-location-picker"
              >
                <MapPin className="w-4 h-4 text-primary" />
                <span className="text-sm font-medium flex-1 text-left truncate">
                  {locationName || "Seleziona posizione"}
                </span>
                <ChevronDown className="w-4 h-4 text-muted-foreground" />
              </button>
              
              <FilterPills filters={filters} setFilters={setFilters} />
              <button
                onClick={() => setCategorySheetOpen(true)}
                className="mt-2 flex items-center gap-2 px-4 py-2 bg-white rounded-full shadow-sm"
                data-testid="list-category-filter"
              >
                <Filter className="w-4 h-4" />
                <span className="text-sm font-medium">
                  {selectedCategories.length > 0
                    ? `${selectedCategories.length} categorie`
                    : "Tutte le categorie"}
                </span>
              </button>
            </div>

            {loading ? (
              <div className="space-y-4">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="h-64 rounded-2xl skeleton" />
                ))}
              </div>
            ) : events.length === 0 ? (
              <div className="text-center py-12">
                <MapPin className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
                <p className="text-muted-foreground">Nessun evento trovato</p>
              </div>
            ) : (
              <div className="space-y-4">
                {events.map((event, idx) => (
                  <div
                    key={event.event_id}
                    className={`animate-fade-in-up stagger-${(idx % 5) + 1}`}
                  >
                    <EventCard event={event} onClick={handleEventClick} />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Mobile Bottom Navigation */}
        <nav className="fixed bottom-6 left-1/2 -translate-x-1/2" style={{ zIndex: 1000 }}>
          <div className="nav-pill rounded-full px-2 py-2 flex items-center gap-1">
            <button
              data-testid="view-map-button"
              onClick={() => setViewMode("map")}
              className={`p-3 rounded-full transition-all ${
                viewMode === "map"
                  ? "bg-primary text-white"
                  : "text-muted-foreground hover:bg-muted"
              }`}
            >
              <Map className="w-5 h-5" />
            </button>
            <button
              data-testid="view-list-button"
              onClick={() => setViewMode("list")}
              className={`p-3 rounded-full transition-all ${
                viewMode === "list"
                  ? "bg-primary text-white"
                  : "text-muted-foreground hover:bg-muted"
              }`}
            >
              <List className="w-5 h-5" />
            </button>
          </div>
        </nav>
      </div>

      {/* Category Filter Sheet */}
      <CategoryFilterSheet
        selectedCategories={selectedCategories}
        setSelectedCategories={setSelectedCategories}
        open={categorySheetOpen}
        setOpen={setCategorySheetOpen}
      />
      
      {/* Location Picker Sheet */}
      <LocationPickerSheet
        open={locationSheetOpen}
        setOpen={setLocationSheetOpen}
        currentLocation={userLocation}
        currentLocationName={locationName}
        onSelectLocation={handleSelectLocation}
        onUseGPS={handleUseGPS}
      />
    </div>
  );
}
