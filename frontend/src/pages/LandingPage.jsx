import { useNavigate } from "react-router-dom";
import { useAuth } from "@/App";
import { Button } from "@/components/ui/button";
import { 
  Map, 
  Zap, 
  Sparkles, 
  MapPin,
  Calendar,
  Users,
  ArrowRight
} from "lucide-react";

export default function LandingPage() {
  const navigate = useNavigate();
  const { user, login } = useAuth();

  const features = [
    {
      icon: Map,
      title: "Mappa in tempo reale",
      description: "Vedi tutti gli eventi vicino a te con un solo tap",
    },
    {
      icon: Zap,
      title: "Decisione in 30 secondi",
      description: "Filtri smart per trovare subito cosa fare",
    },
    {
      icon: Sparkles,
      title: "Descrizioni AI",
      description: "Ogni evento viene migliorato dall'intelligenza artificiale",
    },
  ];

  return (
    <div className="min-h-screen bg-background" data-testid="landing-page">
      {/* Hero */}
      <div className="relative overflow-hidden">
        {/* Background Image */}
        <div 
          className="absolute inset-0 bg-cover bg-center"
          style={{
            backgroundImage: `url(https://images.unsplash.com/photo-1762698723009-c369a6fb9925?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA3MDB8MHwxfHNlYXJjaHw0fHxpdGFsaWFuJTIwc3RyZWV0JTIwZm9vZCUyMHZpYnJhbnQlMjBtYXJrZXR8ZW58MHx8fHwxNzc0MTMzMTIwfDA&ixlib=rb-4.1.0&q=85)`
          }}
        >
          <div className="absolute inset-0 bg-gradient-to-b from-black/70 via-black/50 to-background" />
        </div>

        <div className="relative z-10 px-6 pt-16 pb-24 text-center">
          {/* Logo */}
          <h1 className="text-5xl lg:text-7xl font-bold text-white mb-4">
            Whatsup
          </h1>
          <p className="text-xl lg:text-2xl text-white/80 mb-8 max-w-md mx-auto">
            L'app numero 1 in Italia per chi non sa cosa fare oggi
          </p>

          {/* CTA */}
          <div className="flex flex-col sm:flex-row gap-4 justify-center items-center">
            <Button
              size="lg"
              onClick={() => navigate("/")}
              className="rounded-full px-8 py-6 text-lg font-bold bg-primary hover:bg-primary/90 shadow-xl hover:shadow-2xl transition-all hover:scale-105"
              data-testid="explore-button"
            >
              Esplora Eventi
              <ArrowRight className="ml-2 w-5 h-5" />
            </Button>
            {!user && (
              <Button
                size="lg"
                variant="outline"
                onClick={login}
                className="rounded-full px-8 py-6 text-lg font-bold bg-white/10 border-white/30 text-white hover:bg-white/20 backdrop-blur-sm"
                data-testid="login-landing-button"
              >
                Accedi con Google
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* Features */}
      <div className="px-6 py-16">
        <h2 className="text-3xl font-bold text-center mb-12">
          Trova il tuo prossimo evento
        </h2>
        <div className="grid md:grid-cols-3 gap-8 max-w-4xl mx-auto">
          {features.map((feature, idx) => (
            <div
              key={idx}
              className="text-center p-6 rounded-2xl bg-white shadow-sm hover:shadow-xl transition-all animate-fade-in-up"
              style={{ animationDelay: `${idx * 0.1}s` }}
            >
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-primary/10 flex items-center justify-center">
                <feature.icon className="w-8 h-8 text-primary" />
              </div>
              <h3 className="text-xl font-bold mb-2">{feature.title}</h3>
              <p className="text-muted-foreground">{feature.description}</p>
            </div>
          ))}
        </div>
      </div>

      {/* How it works */}
      <div className="px-6 py-16 bg-secondary/30">
        <h2 className="text-3xl font-bold text-center mb-12">
          Come funziona
        </h2>
        <div className="max-w-3xl mx-auto">
          <div className="relative">
            {/* Connection line */}
            <div className="absolute left-8 top-8 bottom-8 w-0.5 bg-primary/20 hidden md:block" />
            
            {[
              { icon: MapPin, title: "Geolocalizzazione", desc: "L'app rileva automaticamente la tua posizione" },
              { icon: Calendar, title: "Filtra per data", desc: "Oggi, domani o weekend - tu scegli" },
              { icon: Users, title: "Partecipa", desc: "RSVP con un tap e incontra nuove persone" },
            ].map((step, idx) => (
              <div key={idx} className="flex items-start gap-6 mb-8">
                <div className="relative z-10 w-16 h-16 rounded-2xl bg-primary flex items-center justify-center flex-shrink-0">
                  <step.icon className="w-8 h-8 text-white" />
                </div>
                <div className="pt-2">
                  <h3 className="text-xl font-bold mb-1">{step.title}</h3>
                  <p className="text-muted-foreground">{step.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Sources */}
      <div className="px-6 py-16">
        <h2 className="text-3xl font-bold text-center mb-4">
          Eventi da tutto il web
        </h2>
        <p className="text-center text-muted-foreground mb-12 max-w-md mx-auto">
          Aggreghiamo automaticamente eventi da Eventbrite, Meetup e Facebook
        </p>
        <div className="flex justify-center gap-8 flex-wrap">
          {["eventbrite", "meetup", "facebook"].map((source) => (
            <div
              key={source}
              className={`source-badge source-${source} text-lg px-6 py-3`}
            >
              {source}
            </div>
          ))}
        </div>
      </div>

      {/* Final CTA */}
      <div className="px-6 py-16 text-center">
        <h2 className="text-3xl font-bold mb-4">
          Cosa aspetti?
        </h2>
        <p className="text-muted-foreground mb-8 max-w-md mx-auto">
          Scopri gli eventi più interessanti vicino a te
        </p>
        <Button
          size="lg"
          onClick={() => navigate("/")}
          className="rounded-full px-10 py-6 text-lg font-bold shadow-xl hover:shadow-2xl transition-all hover:scale-105"
          data-testid="final-cta-button"
        >
          Inizia Ora
          <ArrowRight className="ml-2 w-5 h-5" />
        </Button>
      </div>

      {/* Footer */}
      <footer className="px-6 py-8 border-t text-center">
        <p className="text-sm text-muted-foreground">
          © 2026 Whatsup - L'Italia a portata di tap
        </p>
      </footer>
    </div>
  );
}
