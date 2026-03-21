import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { MapContainer, TileLayer, Marker } from "react-leaflet";
import L from "leaflet";
import { useAuth } from "@/App";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { 
  ArrowLeft, 
  MapPin, 
  Clock, 
  Calendar, 
  Users, 
  Share2, 
  Bookmark,
  BookmarkCheck,
  ExternalLink,
  Sparkles,
  Check
} from "lucide-react";
import { toast } from "sonner";

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
const API = `${BACKEND_URL}/api`;

// Category colors
const CATEGORY_COLORS = {
  music: "#FF4500",
  sport: "#00A86B",
  art: "#0057B7",
  food: "#FFD700",
  nightlife: "#800080",
  networking: "#FF6B33",
  teatro: "#D32F2F",
  outdoor: "#2E8B57",
  famiglia: "#FF69B4",
  altro: "#808080",
};

const CATEGORY_NAMES = {
  music: "Musica",
  sport: "Sport",
  art: "Arte",
  food: "Food & Drink",
  nightlife: "Nightlife",
  networking: "Networking",
  teatro: "Teatro",
  outdoor: "Outdoor",
  famiglia: "Famiglia",
  altro: "Altro",
};

// Custom marker icon
const createMarkerIcon = (category) => {
  const color = CATEGORY_COLORS[category] || "#808080";
  return L.divIcon({
    className: "custom-marker",
    html: `<div class="marker-pin" style="background-color: ${color}">
      <span class="marker-icon"></span>
    </div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 32],
  });
};

export default function EventDetailPage() {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const { user, login } = useAuth();
  
  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [hasRsvp, setHasRsvp] = useState(false);
  const [isSaved, setIsSaved] = useState(false);
  const [rsvpLoading, setRsvpLoading] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);

  // Fetch event details
  useEffect(() => {
    const fetchEvent = async () => {
      try {
        const response = await fetch(`${API}/events/${eventId}`);
        if (response.ok) {
          const data = await response.json();
          setEvent(data);
        } else {
          toast.error("Evento non trovato");
          navigate("/");
        }
      } catch (error) {
        console.error("Error fetching event:", error);
        toast.error("Errore nel caricamento dell'evento");
      } finally {
        setLoading(false);
      }
    };

    fetchEvent();
  }, [eventId, navigate]);

  // Check RSVP and save status
  useEffect(() => {
    const checkStatus = async () => {
      if (!user || !eventId) return;

      try {
        const [rsvpRes, saveRes] = await Promise.all([
          fetch(`${API}/events/${eventId}/rsvp-status`, { credentials: "include" }),
          fetch(`${API}/events/${eventId}/save-status`, { credentials: "include" }),
        ]);

        if (rsvpRes.ok) {
          const rsvpData = await rsvpRes.json();
          setHasRsvp(rsvpData.has_rsvp);
        }

        if (saveRes.ok) {
          const saveData = await saveRes.json();
          setIsSaved(saveData.is_saved);
        }
      } catch (error) {
        console.error("Error checking status:", error);
      }
    };

    checkStatus();
  }, [user, eventId]);

  const handleRsvp = async () => {
    if (!user) {
      login();
      return;
    }

    setRsvpLoading(true);
    try {
      if (hasRsvp) {
        const response = await fetch(`${API}/events/${eventId}/rsvp`, {
          method: "DELETE",
          credentials: "include",
        });
        if (response.ok) {
          setHasRsvp(false);
          toast.success("RSVP cancellato");
        }
      } else {
        const response = await fetch(`${API}/events/${eventId}/rsvp`, {
          method: "POST",
          credentials: "include",
        });
        if (response.ok) {
          setHasRsvp(true);
          toast.success("RSVP confermato! Ci vediamo all'evento");
        } else {
          const error = await response.json();
          toast.error(error.detail || "Errore nel RSVP");
        }
      }
    } catch (error) {
      console.error("RSVP error:", error);
      toast.error("Errore durante il RSVP");
    } finally {
      setRsvpLoading(false);
    }
  };

  const handleSave = async () => {
    if (!user) {
      login();
      return;
    }

    setSaveLoading(true);
    try {
      if (isSaved) {
        const response = await fetch(`${API}/events/${eventId}/save`, {
          method: "DELETE",
          credentials: "include",
        });
        if (response.ok) {
          setIsSaved(false);
          toast.success("Rimosso dai salvati");
        }
      } else {
        const response = await fetch(`${API}/events/${eventId}/save`, {
          method: "POST",
          credentials: "include",
        });
        if (response.ok) {
          setIsSaved(true);
          toast.success("Evento salvato");
        } else {
          const error = await response.json();
          toast.error(error.detail || "Errore nel salvataggio");
        }
      }
    } catch (error) {
      console.error("Save error:", error);
      toast.error("Errore durante il salvataggio");
    } finally {
      setSaveLoading(false);
    }
  };

  const handleShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: event.title,
          text: `Guarda questo evento su Whatsup: ${event.title}`,
          url: window.location.href,
        });
      } catch (error) {
        if (error.name !== "AbortError") {
          console.error("Share error:", error);
        }
      }
    } else {
      navigator.clipboard.writeText(window.location.href);
      toast.success("Link copiato negli appunti");
    }
  };

  const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString("it-IT", {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    });
  };

  const formatTime = (dateStr) => {
    const date = new Date(dateStr);
    return date.toLocaleTimeString("it-IT", {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <div className="h-64 skeleton" />
        <div className="p-6 space-y-4">
          <div className="h-8 w-3/4 skeleton rounded-lg" />
          <div className="h-4 w-1/2 skeleton rounded-lg" />
          <div className="h-32 skeleton rounded-lg" />
        </div>
      </div>
    );
  }

  if (!event) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <p>Evento non trovato</p>
      </div>
    );
  }

  const categoryColor = CATEGORY_COLORS[event.category] || "#808080";

  return (
    <div className="min-h-screen bg-background pb-24" data-testid="event-detail-page">
      {/* Hero Image */}
      <div className="relative h-72 lg:h-96">
        <img
          src={event.image_url}
          alt={event.title}
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
        
        {/* Back Button */}
        <button
          onClick={() => navigate(-1)}
          className="absolute top-4 left-4 p-3 rounded-full bg-white/90 backdrop-blur-sm shadow-lg hover:bg-white transition-colors"
          data-testid="back-button"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        {/* Action Buttons */}
        <div className="absolute top-4 right-4 flex gap-2">
          <button
            onClick={handleShare}
            className="p-3 rounded-full bg-white/90 backdrop-blur-sm shadow-lg hover:bg-white transition-colors"
            data-testid="share-button"
          >
            <Share2 className="w-5 h-5" />
          </button>
          <button
            onClick={handleSave}
            disabled={saveLoading}
            className={`p-3 rounded-full shadow-lg transition-colors ${
              isSaved
                ? "bg-primary text-white"
                : "bg-white/90 backdrop-blur-sm hover:bg-white"
            }`}
            data-testid="save-button"
          >
            {isSaved ? (
              <BookmarkCheck className="w-5 h-5" />
            ) : (
              <Bookmark className="w-5 h-5" />
            )}
          </button>
        </div>

        {/* Category Badge */}
        <div className="absolute bottom-4 left-4">
          <span
            className="px-4 py-2 rounded-full text-sm font-bold uppercase tracking-wider text-white"
            style={{ backgroundColor: categoryColor }}
          >
            {CATEGORY_NAMES[event.category] || event.category}
          </span>
        </div>
      </div>

      {/* Content */}
      <div className="px-4 lg:px-8 max-w-4xl mx-auto">
        <div className="-mt-8 relative z-10">
          <Card className="p-6 rounded-2xl shadow-xl border-0">
            <h1 className="text-2xl lg:text-3xl font-bold mb-4">{event.title}</h1>

            {/* Meta Info */}
            <div className="space-y-3 mb-6">
              <div className="flex items-center gap-3 text-muted-foreground">
                <Calendar className="w-5 h-5 flex-shrink-0" />
                <span className="font-medium">{formatDate(event.date)}</span>
              </div>
              <div className="flex items-center gap-3 text-muted-foreground">
                <Clock className="w-5 h-5 flex-shrink-0" />
                <span className="font-medium">{formatTime(event.date)}</span>
              </div>
              <div className="flex items-center gap-3 text-muted-foreground">
                <MapPin className="w-5 h-5 flex-shrink-0" />
                <span className="font-medium">{event.address}, {event.city}</span>
              </div>
              <div className="flex items-center gap-3 text-muted-foreground">
                <Users className="w-5 h-5 flex-shrink-0" />
                <span className="font-medium">{event.participants_count} partecipanti</span>
              </div>
            </div>

            {/* Price */}
            <div className="mb-6 p-4 bg-muted/50 rounded-xl">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Prezzo</span>
                <span className="text-xl font-bold">
                  {event.is_free ? (
                    <span className="text-green-600">Gratuito</span>
                  ) : (
                    `€${event.price.toFixed(2)}`
                  )}
                </span>
              </div>
            </div>

            {/* AI Description */}
            {event.ai_description && (
              <div className="mb-6">
                <div className="flex items-center gap-2 mb-3">
                  <Sparkles className="w-5 h-5 text-primary" />
                  <span className="text-sm font-medium text-primary">Descrizione AI</span>
                </div>
                <p className="text-lg leading-relaxed">{event.ai_description}</p>
              </div>
            )}

            {/* Original Description */}
            <div className="mb-6">
              <h3 className="font-bold mb-2">Dettagli evento</h3>
              <p className="text-muted-foreground leading-relaxed">{event.description}</p>
            </div>

            {/* Source */}
            <div className="mb-6 flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Fonte:</span>
              <span className={`source-badge source-${event.source}`}>
                {event.source}
              </span>
              {event.source_url && (
                <a
                  href={event.source_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary hover:underline flex items-center gap-1"
                >
                  <ExternalLink className="w-4 h-4" />
                </a>
              )}
            </div>

            {/* Mini Map */}
            <div className="rounded-xl overflow-hidden h-48 mb-6">
              <MapContainer
                center={[event.lat, event.lng]}
                zoom={15}
                className="h-full w-full"
                zoomControl={false}
                dragging={false}
                scrollWheelZoom={false}
              >
                <TileLayer
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <Marker
                  position={[event.lat, event.lng]}
                  icon={createMarkerIcon(event.category)}
                />
              </MapContainer>
            </div>

            {/* Open in Maps */}
            <a
              href={`https://www.google.com/maps/dir/?api=1&destination=${event.lat},${event.lng}`}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-2 p-3 bg-muted rounded-xl text-sm font-medium hover:bg-muted/80 transition-colors"
              data-testid="open-maps-button"
            >
              <MapPin className="w-4 h-4" />
              Apri in Google Maps
            </a>
          </Card>
        </div>
      </div>

      {/* Fixed Bottom CTA */}
      <div className="fixed bottom-0 left-0 right-0 p-4 bg-background/80 backdrop-blur-xl border-t">
        <div className="max-w-4xl mx-auto">
          <Button
            onClick={handleRsvp}
            disabled={rsvpLoading}
            className={`w-full h-14 text-lg font-bold rounded-full ${
              hasRsvp
                ? "bg-green-600 hover:bg-green-700"
                : "bg-primary hover:bg-primary/90"
            }`}
            data-testid="rsvp-button"
          >
            {rsvpLoading ? (
              "Caricamento..."
            ) : hasRsvp ? (
              <>
                <Check className="w-5 h-5 mr-2" />
                Partecipo!
              </>
            ) : event.is_free ? (
              "Partecipa Gratis"
            ) : (
              `Partecipa - €${event.price.toFixed(2)}`
            )}
          </Button>
        </div>
      </div>
    </div>
  );
}
