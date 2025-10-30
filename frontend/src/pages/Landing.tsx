import { Button } from "@/components/ui/button";
import { Music2 } from "lucide-react";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

const API_BASE_URL = "https://audiance.onrender.com";

const Landing = () => {
  const navigate = useNavigate();

  useEffect(() => {
    // Check if user is already authenticated
    const token = localStorage.getItem('jwtToken');
    if (token) {
      navigate('/create');
    }
  }, [navigate]);

  const handleSpotifyLogin = () => {
    // Redirect to backend Spotify OAuth endpoint
    window.location.href = `${API_BASE_URL}/oauth2/authorization/spotify`;
  };

  return (
    <div className="min-h-screen bg-gradient-hero relative overflow-hidden">
      {/* Animated background waves */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute bottom-0 left-0 right-0 h-32 flex items-end justify-around gap-2 px-8 pb-8 opacity-20">
          {[...Array(20)].map((_, i) => (
            <div
              key={i}
              className="w-2 bg-primary rounded-full animate-wave"
              style={{
                height: `${Math.random() * 100 + 50}px`,
                animationDelay: `${i * 0.1}s`,
              }}
            />
          ))}
        </div>
      </div>

      {/* Main content */}
      <div className="relative z-10 flex flex-col items-center justify-center min-h-screen px-4">
        <div className="text-center space-y-8 max-w-4xl animate-fade-in">
          {/* Logo/Icon */}
          <div className="flex justify-center mb-8">
            <div className="relative">
              <Music2 className="w-20 h-20 text-primary animate-pulse-glow" />
              <div className="absolute inset-0 bg-primary/20 blur-3xl rounded-full" />
            </div>
          </div>

          {/* Headline */}
          <h1 className="text-5xl md:text-7xl font-bold bg-gradient-primary bg-clip-text text-transparent leading-tight">
            Describe Your Vibe.
            <br />
            Get Your Soundtrack.
          </h1>

          {/* Subheading */}
          <p className="text-xl md:text-2xl text-muted-foreground max-w-2xl mx-auto">
            Turn your thoughts into personalized playlists powered by AI. Your mood, your music, instantly.
          </p>

          {/* CTA Button */}
          <div className="pt-8">
            <Button
              variant="hero"
              size="lg"
              onClick={handleSpotifyLogin}
              className="text-lg px-12 py-8 h-auto font-bold tracking-wide transform hover:scale-105 transition-transform"
            >
              <Music2 className="w-6 h-6" />
              Connect with Spotify
            </Button>
          </div>

          {/* Feature hints */}
          <div className="pt-12 grid grid-cols-1 md:grid-cols-3 gap-6 text-sm text-muted-foreground">
            <div className="space-y-2">
              <div className="text-primary font-semibold">🎯 AI-Powered</div>
              <div>Smart playlist generation from natural language</div>
            </div>
            <div className="space-y-2">
              <div className="text-secondary font-semibold">⚡ Instant</div>
              <div>Your soundtrack ready in seconds</div>
            </div>
            <div className="space-y-2">
              <div className="text-accent font-semibold">🎨 Personal</div>
              <div>Tailored to your unique taste</div>
            </div>
          </div>
        </div>
      </div>

      {/* Footer note */}
      <div className="absolute bottom-4 left-0 right-0 text-center text-xs text-muted-foreground">
        Powered by Audynce
      </div>
    </div>
  );
};

export default Landing;
