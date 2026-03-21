import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/App";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { ScrollArea } from "@/components/ui/scroll-area";
import { 
  ArrowLeft, 
  Bookmark, 
  Calendar, 
  LogOut,
  MapPin,
  Clock,
  Settings
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

// Mini Event Card
const MiniEventCard = ({ event, onClick }) => {
  const categoryColor = CATEGORY_COLORS[event.category] || "#808080";
  
  const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString("it-IT", {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  return (
    <Card
      className="flex overflow-hidden cursor-pointer hover:shadow-lg transition-all border-0 bg-white"
      onClick={() => onClick(event)}
      data-testid={`profile-event-${event.event_id}`}
    >
      <div className="w-24 h-24 flex-shrink-0">
        <img
          src={event.image_url}
          alt={event.title}
          className="w-full h-full object-cover"
        />
      </div>
      <div className="flex-1 p-3 flex flex-col justify-center">
        <div className="flex items-center gap-2 mb-1">
          <span
            className="w-2 h-2 rounded-full"
            style={{ backgroundColor: categoryColor }}
          />
          <span className="text-xs text-muted-foreground">
            {CATEGORY_NAMES[event.category] || event.category}
          </span>
        </div>
        <h4 className="font-bold text-sm line-clamp-1">{event.title}</h4>
        <div className="flex items-center gap-2 mt-1 text-xs text-muted-foreground">
          <Clock className="w-3 h-3" />
          <span>{formatDate(event.date)}</span>
        </div>
        <div className="flex items-center gap-2 mt-0.5 text-xs text-muted-foreground">
          <MapPin className="w-3 h-3" />
          <span className="truncate">{event.city}</span>
        </div>
      </div>
    </Card>
  );
};

// Empty State Component
const EmptyState = ({ icon: Icon, title, description }) => (
  <div className="text-center py-12">
    <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-muted flex items-center justify-center">
      <Icon className="w-8 h-8 text-muted-foreground" />
    </div>
    <h3 className="font-bold mb-1">{title}</h3>
    <p className="text-sm text-muted-foreground">{description}</p>
  </div>
);

export default function ProfilePage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [savedEvents, setSavedEvents] = useState([]);
  const [rsvpEvents, setRsvpEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        const [savedRes, rsvpRes] = await Promise.all([
          fetch(`${API}/user/saved-events`, { credentials: "include" }),
          fetch(`${API}/user/rsvps`, { credentials: "include" }),
        ]);

        if (savedRes.ok) {
          const savedData = await savedRes.json();
          setSavedEvents(savedData);
        }

        if (rsvpRes.ok) {
          const rsvpData = await rsvpRes.json();
          setRsvpEvents(rsvpData);
        }
      } catch (error) {
        console.error("Error fetching user data:", error);
        toast.error("Errore nel caricamento dei dati");
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, []);

  const handleEventClick = (event) => {
    navigate(`/event/${event.event_id}`);
  };

  const handleLogout = async () => {
    await logout();
    navigate("/");
    toast.success("Logout effettuato");
  };

  const getInitials = (name) => {
    if (!name) return "U";
    return name
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2);
  };

  return (
    <div className="min-h-screen bg-background" data-testid="profile-page">
      {/* Header */}
      <header className="sticky top-0 z-50 bg-background/80 backdrop-blur-xl border-b">
        <div className="flex items-center justify-between p-4">
          <button
            onClick={() => navigate(-1)}
            className="p-2 rounded-full hover:bg-muted transition-colors"
            data-testid="profile-back-button"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <h1 className="font-bold text-lg">Profilo</h1>
          <button
            onClick={handleLogout}
            className="p-2 rounded-full hover:bg-muted transition-colors text-destructive"
            data-testid="logout-button"
          >
            <LogOut className="w-5 h-5" />
          </button>
        </div>
      </header>

      {/* User Info */}
      <div className="p-6 text-center">
        <Avatar className="w-24 h-24 mx-auto mb-4 ring-4 ring-primary/20">
          <AvatarImage src={user?.picture} alt={user?.name} />
          <AvatarFallback className="text-2xl bg-primary text-white">
            {getInitials(user?.name)}
          </AvatarFallback>
        </Avatar>
        <h2 className="text-2xl font-bold">{user?.name}</h2>
        <p className="text-muted-foreground">{user?.email}</p>
        
        {/* Stats */}
        <div className="flex justify-center gap-8 mt-6">
          <div className="text-center">
            <div className="text-2xl font-bold text-primary">{rsvpEvents.length}</div>
            <div className="text-sm text-muted-foreground">Eventi</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-primary">{savedEvents.length}</div>
            <div className="text-sm text-muted-foreground">Salvati</div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="rsvps" className="px-4 pb-6">
        <TabsList className="w-full grid grid-cols-2 mb-4">
          <TabsTrigger value="rsvps" data-testid="tab-rsvps">
            <Calendar className="w-4 h-4 mr-2" />
            I miei eventi
          </TabsTrigger>
          <TabsTrigger value="saved" data-testid="tab-saved">
            <Bookmark className="w-4 h-4 mr-2" />
            Salvati
          </TabsTrigger>
        </TabsList>

        <TabsContent value="rsvps">
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-24 rounded-xl skeleton" />
              ))}
            </div>
          ) : rsvpEvents.length === 0 ? (
            <EmptyState
              icon={Calendar}
              title="Nessun evento"
              description="Non hai ancora confermato la partecipazione a nessun evento"
            />
          ) : (
            <ScrollArea className="h-[400px]">
              <div className="space-y-3">
                {rsvpEvents.map((event) => (
                  <MiniEventCard
                    key={event.event_id}
                    event={event}
                    onClick={handleEventClick}
                  />
                ))}
              </div>
            </ScrollArea>
          )}
        </TabsContent>

        <TabsContent value="saved">
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-24 rounded-xl skeleton" />
              ))}
            </div>
          ) : savedEvents.length === 0 ? (
            <EmptyState
              icon={Bookmark}
              title="Nessun evento salvato"
              description="Salva gli eventi che ti interessano per ritrovarli qui"
            />
          ) : (
            <ScrollArea className="h-[400px]">
              <div className="space-y-3">
                {savedEvents.map((event) => (
                  <MiniEventCard
                    key={event.event_id}
                    event={event}
                    onClick={handleEventClick}
                  />
                ))}
              </div>
            </ScrollArea>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
