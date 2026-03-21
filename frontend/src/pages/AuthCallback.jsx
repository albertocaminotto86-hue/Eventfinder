import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/App";
import { toast } from "sonner";

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
const API = `${BACKEND_URL}/api`;

export default function AuthCallback() {
  const navigate = useNavigate();
  const { setUser } = useAuth();
  const hasProcessed = useRef(false);

  useEffect(() => {
    // Use useRef to prevent double processing in StrictMode
    if (hasProcessed.current) return;
    hasProcessed.current = true;

    const processAuth = async () => {
      // Extract session_id from URL fragment
      const hash = window.location.hash;
      const sessionIdMatch = hash.match(/session_id=([^&]+)/);
      
      if (!sessionIdMatch) {
        toast.error("Errore di autenticazione");
        navigate("/");
        return;
      }

      const sessionId = sessionIdMatch[1];

      try {
        // Exchange session_id for session_token
        const response = await fetch(`${API}/auth/session`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({ session_id: sessionId }),
        });

        if (!response.ok) {
          throw new Error("Auth exchange failed");
        }

        const data = await response.json();
        setUser(data.user);
        
        // Clear the hash and navigate to home
        window.history.replaceState(null, "", "/");
        navigate("/", { state: { user: data.user } });
        toast.success(`Benvenuto, ${data.user.name}!`);
      } catch (error) {
        console.error("Auth callback error:", error);
        toast.error("Errore durante l'autenticazione");
        navigate("/");
      }
    };

    processAuth();
  }, [navigate, setUser]);

  return (
    <div className="min-h-screen bg-background flex items-center justify-center">
      <div className="text-center">
        <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-primary/10 flex items-center justify-center animate-pulse-soft">
          <div className="w-8 h-8 rounded-lg bg-primary/30" />
        </div>
        <p className="text-muted-foreground">Autenticazione in corso...</p>
      </div>
    </div>
  );
}
