import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/App";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { 
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { 
  ArrowLeft, 
  Play,
  Check,
  X,
  RefreshCw,
  MapPin,
  Calendar,
  ExternalLink,
  AlertCircle,
  CheckCircle,
  Clock,
  TrendingUp,
  Users,
  Map,
  Loader2,
  Crosshair
} from "lucide-react";
import { toast } from "sonner";

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
const API = `${BACKEND_URL}/api`;

// Category colors
const CATEGORY_COLORS = {
  music: "#FF4500",
  concerto: "#FF4500",
  sport: "#00A86B",
  art: "#0057B7",
  arte: "#0057B7",
  food: "#FFD700",
  nightlife: "#800080",
  aperitivo: "#800080",
  networking: "#FF6B33",
  teatro: "#D32F2F",
  outdoor: "#2E8B57",
  famiglia: "#FF69B4",
  altro: "#808080",
};

// Stats Card Component
const StatCard = ({ title, value, icon: Icon, trend, color = "primary" }) => (
  <Card className="border-0 shadow-sm">
    <CardContent className="p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-muted-foreground mb-1">{title}</p>
          <p className="text-3xl font-bold">{value}</p>
          {trend && (
            <p className="text-xs text-green-600 mt-1 flex items-center gap-1">
              <TrendingUp className="w-3 h-3" />
              {trend}
            </p>
          )}
        </div>
        <div className={`w-12 h-12 rounded-xl bg-${color}/10 flex items-center justify-center`}>
          <Icon className={`w-6 h-6 text-${color}`} />
        </div>
      </div>
    </CardContent>
  </Card>
);

// Review Event Card
const ReviewEventCard = ({ event, onApprove, onDiscard, loading }) => {
  const categoryColor = CATEGORY_COLORS[event.categoria] || "#808080";
  
  const confidenceColor = (value) => {
    if (value >= 0.8) return "text-green-600";
    if (value >= 0.5) return "text-yellow-600";
    return "text-red-600";
  };

  return (
    <Card className="border-0 shadow-sm overflow-hidden">
      <div className="flex">
        {event.image_url && (
          <div className="w-32 h-32 flex-shrink-0">
            <img 
              src={event.image_url} 
              alt={event.titolo}
              className="w-full h-full object-cover"
            />
          </div>
        )}
        <div className="flex-1 p-4">
          <div className="flex items-start justify-between mb-2">
            <div>
              <Badge 
                className="mb-2"
                style={{ backgroundColor: categoryColor, color: "white" }}
              >
                {event.categoria}
              </Badge>
              <h3 className="font-bold text-lg">{event.titolo || "Titolo non disponibile"}</h3>
            </div>
            <Badge variant="outline" className="text-xs">
              {event.source_platform}
            </Badge>
          </div>
          
          <p className="text-sm text-muted-foreground mb-3 line-clamp-2">
            {event.descrizione_breve}
          </p>
          
          <div className="grid grid-cols-2 gap-2 text-xs mb-3">
            <div className="flex items-center gap-1">
              <Calendar className="w-3 h-3" />
              <span>{event.data_iso || "Data incerta"}</span>
              {event.ora_inizio && <span>• {event.ora_inizio}</span>}
            </div>
            <div className="flex items-center gap-1">
              <MapPin className="w-3 h-3" />
              <span className="truncate">{event.citta}</span>
            </div>
          </div>
          
          {/* Confidence Scores */}
          <div className="flex gap-4 text-xs mb-3">
            <div className="flex items-center gap-1">
              <span className="text-muted-foreground">Data:</span>
              <span className={confidenceColor(event.confidence_data)}>
                {(event.confidence_data * 100).toFixed(0)}%
              </span>
            </div>
            <div className="flex items-center gap-1">
              <span className="text-muted-foreground">Luogo:</span>
              <span className={confidenceColor(event.confidence_luogo)}>
                {(event.confidence_luogo * 100).toFixed(0)}%
              </span>
            </div>
            <div className="flex items-center gap-1">
              <span className="text-muted-foreground">Categoria:</span>
              <span className={confidenceColor(event.confidence_categoria)}>
                {(event.confidence_categoria * 100).toFixed(0)}%
              </span>
            </div>
          </div>
          
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              onClick={() => onApprove(event.dedup_hash)}
              disabled={loading}
              className="bg-green-600 hover:bg-green-700"
              data-testid={`approve-${event.dedup_hash}`}
            >
              <Check className="w-4 h-4 mr-1" />
              Approva
            </Button>
            <Button
              size="sm"
              variant="destructive"
              onClick={() => onDiscard(event.dedup_hash)}
              disabled={loading}
              data-testid={`discard-${event.dedup_hash}`}
            >
              <X className="w-4 h-4 mr-1" />
              Scarta
            </Button>
            {event.source_url && (
              <a
                href={event.source_url}
                target="_blank"
                rel="noopener noreferrer"
                className="ml-auto text-primary hover:underline flex items-center gap-1 text-sm"
              >
                <ExternalLink className="w-4 h-4" />
                Fonte
              </a>
            )}
          </div>
        </div>
      </div>
    </Card>
  );
};

// Discovered Event Card (simpler)
const DiscoveredEventCard = ({ event }) => {
  const categoryColor = CATEGORY_COLORS[event.categoria] || "#808080";
  
  const statusBadge = {
    published: { color: "bg-green-100 text-green-800", label: "Pubblicato" },
    review: { color: "bg-yellow-100 text-yellow-800", label: "In revisione" },
    discarded: { color: "bg-red-100 text-red-800", label: "Scartato" },
  };
  
  const status = statusBadge[event.status] || statusBadge.review;

  return (
    <Card className="border-0 shadow-sm p-4">
      <div className="flex items-start justify-between mb-2">
        <div className="flex items-center gap-2">
          <Badge style={{ backgroundColor: categoryColor, color: "white" }}>
            {event.categoria}
          </Badge>
          <Badge className={status.color}>{status.label}</Badge>
        </div>
        <span className="text-xs text-muted-foreground">{event.source_platform}</span>
      </div>
      <h4 className="font-semibold mb-1">{event.titolo || "Senza titolo"}</h4>
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span>{event.citta}</span>
        <span>{event.data_iso}</span>
      </div>
    </Card>
  );
};

export default function AdminPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [reviewQueue, setReviewQueue] = useState([]);
  const [discoveredEvents, setDiscoveredEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [jobRunning, setJobRunning] = useState(false);
  const [gpsLoading, setGpsLoading] = useState(false);
  const [selectedCity, setSelectedCity] = useState("all");
  const [cities, setCities] = useState([]);

  // Fetch stats
  const fetchStats = useCallback(async () => {
    try {
      const response = await fetch(`${API}/admin/stats`);
      if (response.ok) {
        const data = await response.json();
        setStats(data);
      }
    } catch (error) {
      console.error("Error fetching stats:", error);
    }
  }, []);

  // Fetch review queue
  const fetchReviewQueue = useCallback(async () => {
    try {
      const url = selectedCity === "all" 
        ? `${API}/discovery/review-queue`
        : `${API}/discovery/review-queue?city=${selectedCity}`;
      const response = await fetch(url);
      if (response.ok) {
        const data = await response.json();
        setReviewQueue(data.events || []);
      }
    } catch (error) {
      console.error("Error fetching review queue:", error);
    }
  }, [selectedCity]);

  // Fetch discovered events
  const fetchDiscoveredEvents = useCallback(async () => {
    try {
      const response = await fetch(`${API}/discovery/events?limit=50`);
      if (response.ok) {
        const data = await response.json();
        setDiscoveredEvents(data.events || []);
      }
    } catch (error) {
      console.error("Error fetching discovered events:", error);
    }
  }, []);

  // Fetch cities
  const fetchCities = useCallback(async () => {
    try {
      const response = await fetch(`${API}/admin/cities`);
      if (response.ok) {
        const data = await response.json();
        setCities(data.cities || []);
      }
    } catch (error) {
      console.error("Error fetching cities:", error);
    }
  }, []);

  // Initial load
  useEffect(() => {
    const loadAll = async () => {
      setLoading(true);
      await Promise.all([
        fetchStats(),
        fetchReviewQueue(),
        fetchDiscoveredEvents(),
        fetchCities()
      ]);
      setLoading(false);
    };
    loadAll();
  }, [fetchStats, fetchReviewQueue, fetchDiscoveredEvents, fetchCities]);

  // Refresh when city changes
  useEffect(() => {
    fetchReviewQueue();
  }, [selectedCity, fetchReviewQueue]);

  // Run discovery job
  const runDiscoveryJob = async () => {
    setJobRunning(true);
    try {
      const response = await fetch(`${API}/jobs/discovery/run`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          cities: selectedCity === "all" ? ["Roma", "Milano"] : [selectedCity],
          max_queries: 5,
          max_pages: 10
        })
      });
      
      if (response.ok) {
        const result = await response.json();
        toast.success(`Discovery completata: ${result.events_discovered} eventi trovati`);
        // Refresh data
        await fetchStats();
        await fetchReviewQueue();
        await fetchDiscoveredEvents();
      } else {
        const error = await response.json();
        toast.error(error.detail || "Errore durante la discovery");
      }
    } catch (error) {
      console.error("Discovery error:", error);
      toast.error("Errore di connessione");
    } finally {
      setJobRunning(false);
    }
  };

  // Run discovery from GPS location
  const runGPSDiscovery = () => {
    setGpsLoading(true);
    
    if (!navigator.geolocation) {
      toast.error("Geolocalizzazione non supportata");
      setGpsLoading(false);
      return;
    }
    
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const lat = position.coords.latitude;
        const lng = position.coords.longitude;
        
        toast.info(`Scanning eventi vicino a te...`);
        
        try {
          const response = await fetch(`${API}/jobs/discovery/run`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              lat: lat,
              lng: lng,
              max_queries: 5,
              max_pages: 10
            })
          });
          
          if (response.ok) {
            const result = await response.json();
            toast.success(`Trovati ${result.events_discovered} eventi nella tua zona!`);
            await fetchStats();
            await fetchReviewQueue();
            await fetchDiscoveredEvents();
          } else {
            const error = await response.json();
            toast.error(error.detail || "Errore durante la discovery");
          }
        } catch (error) {
          console.error("GPS Discovery error:", error);
          toast.error("Errore di connessione");
        } finally {
          setGpsLoading(false);
        }
      },
      (error) => {
        console.log("Geolocation error:", error);
        toast.error("Attiva i servizi di localizzazione nelle impostazioni del sistema");
        setGpsLoading(false);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  // Approve event
  const approveEvent = async (dedupHash) => {
    setActionLoading(true);
    try {
      const response = await fetch(`${API}/discovery/events/${dedupHash}/approve`, {
        method: "PATCH"
      });
      
      if (response.ok) {
        toast.success("Evento approvato e pubblicato");
        await fetchReviewQueue();
        await fetchStats();
      } else {
        toast.error("Errore durante l'approvazione");
      }
    } catch (error) {
      toast.error("Errore di connessione");
    } finally {
      setActionLoading(false);
    }
  };

  // Discard event
  const discardEvent = async (dedupHash) => {
    setActionLoading(true);
    try {
      const response = await fetch(`${API}/discovery/events/${dedupHash}/discard`, {
        method: "PATCH"
      });
      
      if (response.ok) {
        toast.success("Evento scartato");
        await fetchReviewQueue();
        await fetchStats();
      } else {
        toast.error("Errore durante lo scarto");
      }
    } catch (error) {
      toast.error("Errore di connessione");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background" data-testid="admin-page">
      {/* Header */}
      <header className="sticky top-0 z-50 bg-background/80 backdrop-blur-xl border-b">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between p-4 max-w-7xl mx-auto gap-3">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate("/")}
              className="p-2 rounded-full hover:bg-muted transition-colors"
              data-testid="admin-back-button"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <h1 className="font-bold text-xl">Admin Panel</h1>
              <p className="text-sm text-muted-foreground hidden sm:block">Gestione eventi e discovery</p>
            </div>
          </div>
          
          <div className="flex items-center gap-2 sm:gap-3">
            <Select value={selectedCity} onValueChange={setSelectedCity}>
              <SelectTrigger className="w-32 sm:w-40" data-testid="city-select">
                <SelectValue placeholder="Città" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutte le città</SelectItem>
                {cities.map((city) => (
                  <SelectItem key={city} value={city}>{city}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            
            {/* GPS Discovery Button */}
            <Button
              onClick={runGPSDiscovery}
              disabled={gpsLoading || jobRunning}
              variant="outline"
              size="icon"
              className="flex-shrink-0"
              data-testid="gps-discovery-button"
              title="Discovery dalla posizione attuale"
            >
              {gpsLoading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Crosshair className="w-4 h-4" />
              )}
            </Button>
            
            <Button
              onClick={runDiscoveryJob}
              disabled={jobRunning || gpsLoading}
              data-testid="run-discovery-button"
              className="flex-shrink-0"
            >
              {jobRunning ? (
                <>
                  <Loader2 className="w-4 h-4 sm:mr-2 animate-spin" />
                  <span className="hidden sm:inline">In corso...</span>
                </>
              ) : (
                <>
                  <Play className="w-4 h-4 sm:mr-2" />
                  <span className="hidden sm:inline">Avvia Discovery</span>
                  <span className="sm:hidden">Avvia</span>
                </>
              )}
            </Button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto p-6">
        {/* Stats Grid */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatCard
            title="Eventi Totali"
            value={stats?.total_events || 0}
            icon={Calendar}
          />
          <StatCard
            title="In Revisione"
            value={stats?.discovered?.review || 0}
            icon={AlertCircle}
            color="yellow-500"
          />
          <StatCard
            title="Pubblicati"
            value={stats?.discovered?.published || 0}
            icon={CheckCircle}
            color="green-500"
          />
          <StatCard
            title="Utenti"
            value={stats?.total_users || 0}
            icon={Users}
          />
        </div>

        {/* Last Job Status */}
        {stats?.last_discovery_job && stats.last_discovery_job.status !== "idle" && (
          <Card className="mb-6 border-0 shadow-sm">
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={`w-3 h-3 rounded-full ${
                    stats.last_discovery_job.status === "running" ? "bg-yellow-500 animate-pulse" :
                    stats.last_discovery_job.status === "completed" ? "bg-green-500" : "bg-red-500"
                  }`} />
                  <span className="font-medium">
                    Ultimo job: {stats.last_discovery_job.status}
                  </span>
                </div>
                {stats.last_discovery_job.last_run && (
                  <span className="text-sm text-muted-foreground">
                    {new Date(stats.last_discovery_job.last_run).toLocaleString("it-IT")}
                  </span>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        {/* Tabs */}
        <Tabs defaultValue="review" className="space-y-6">
          <TabsList>
            <TabsTrigger value="review" data-testid="tab-review">
              <AlertCircle className="w-4 h-4 mr-2" />
              Coda Revisione ({reviewQueue.length})
            </TabsTrigger>
            <TabsTrigger value="discovered" data-testid="tab-discovered">
              <Map className="w-4 h-4 mr-2" />
              Tutti Scoperti ({discoveredEvents.length})
            </TabsTrigger>
          </TabsList>

          <TabsContent value="review">
            {reviewQueue.length === 0 ? (
              <Card className="border-0 shadow-sm">
                <CardContent className="p-12 text-center">
                  <CheckCircle className="w-12 h-12 mx-auto text-green-500 mb-4" />
                  <h3 className="font-bold text-lg mb-2">Nessun evento in revisione</h3>
                  <p className="text-muted-foreground mb-4">
                    Tutti gli eventi sono stati processati. Avvia una nuova discovery per trovare altri eventi.
                  </p>
                  <Button onClick={runDiscoveryJob} disabled={jobRunning}>
                    <Play className="w-4 h-4 mr-2" />
                    Avvia Discovery
                  </Button>
                </CardContent>
              </Card>
            ) : (
              <div className="space-y-4">
                {reviewQueue.map((event) => (
                  <ReviewEventCard
                    key={event.dedup_hash}
                    event={event}
                    onApprove={approveEvent}
                    onDiscard={discardEvent}
                    loading={actionLoading}
                  />
                ))}
              </div>
            )}
          </TabsContent>

          <TabsContent value="discovered">
            <ScrollArea className="h-[600px]">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {discoveredEvents.map((event) => (
                  <DiscoveredEventCard key={event.dedup_hash} event={event} />
                ))}
              </div>
            </ScrollArea>
          </TabsContent>
        </Tabs>
      </main>
    </div>
  );
}
