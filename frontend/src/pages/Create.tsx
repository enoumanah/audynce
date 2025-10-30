import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Card } from "@/components/ui/card";
import { toast } from "sonner";
import { Sparkles, LogOut, User } from "lucide-react";
import GenreSelector from "@/components/GenreSelector";
import LoadingAnimation from "@/components/LoadingAnimation";

const API_BASE_URL = "https://audiance.onrender.com";

// Default genres list from Spotify API
const DEFAULT_GENRES = [
  "acoustic", "afrobeat", "alt-rock", "alternative", "ambient", "anime", "black-metal",
  "bluegrass", "blues", "bossanova", "brazil", "breakbeat", "british", "cantopop",
  "chicago-house", "children", "chill", "classical", "club", "comedy", "country",
  "dance", "dancehall", "death-metal", "deep-house", "detroit-techno", "disco", "disney",
  "drum-and-bass", "dub", "dubstep", "edm", "electro", "electronic", "emo", "folk",
  "forro", "french", "funk", "garage", "german", "gospel", "goth", "grindcore", "groove",
  "grunge", "guitar", "happy", "hard-rock", "hardcore", "hardstyle", "heavy-metal",
  "hip-hop", "holidays", "honky-tonk", "house", "idm", "indian", "indie", "indie-pop",
  "industrial", "iranian", "j-dance", "j-idol", "j-pop", "j-rock", "jazz", "k-pop",
  "kids", "latin", "latino", "malay", "mandopop", "metal", "metal-misc", "metalcore",
  "minimal-techno", "movies", "mpb", "new-age", "new-release", "opera", "pagode",
  "party", "philippines-opm", "piano", "pop", "pop-film", "post-dubstep", "power-pop",
  "progressive-house", "psych-rock", "punk", "punk-rock", "r-n-b", "rainy-day", "reggae",
  "reggaeton", "road-trip", "rock", "rock-n-roll", "rockabilly", "romance", "sad",
  "salsa", "samba", "sertanejo", "show-tunes", "singer-songwriter", "ska", "sleep",
  "songwriter", "soul", "soundtracks", "spanish", "study", "summer", "swedish",
  "synth-pop", "tango", "techno", "trance", "trip-hop", "turkish", "work-out", "world-music"
];

interface HealthResponse {
  status: string;
  availableGenres: string[];
}

const Create = () => {
  const navigate = useNavigate();
  const [genres, setGenres] = useState<string[]>(DEFAULT_GENRES);
  const [prompt, setPrompt] = useState("");
  const [selectedGenres, setSelectedGenres] = useState<string[]>([]);
  const [tracksPerScene, setTracksPerScene] = useState(10);
  const [usePersonalization, setUsePersonalization] = useState(true);
  const [createSpotifyPlaylist, setCreateSpotifyPlaylist] = useState(true);
  const [isPublic, setIsPublic] = useState(true);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    // Check authentication
    const token = localStorage.getItem('jwtToken');
    if (!token) {
      navigate('/');
      return;
    }

    // Fetch available genres
    fetchGenres();
  }, [navigate]);

  const fetchGenres = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/health`);
      if (!response.ok) throw new Error('Failed to fetch genres');
      
      const data: HealthResponse = await response.json();
      if (data.availableGenres && data.availableGenres.length > 0) {
        setGenres(data.availableGenres);
      }
      // If no genres from API, keep using DEFAULT_GENRES
    } catch (error) {
      console.error('Error fetching genres:', error);
      // Keep using DEFAULT_GENRES on error
    }
  };

  const handleGenreToggle = (genre: string) => {
    setSelectedGenres((prev) =>
      prev.includes(genre)
        ? prev.filter((g) => g !== genre)
        : prev.length < 3
        ? [...prev, genre]
        : prev
    );
  };

  const handleGenerate = async () => {
    if (!prompt.trim()) {
      toast.error("Please describe your vibe!");
      return;
    }

    if (selectedGenres.length === 0) {
      toast.error("Please select at least one genre");
      return;
    }

    setIsLoading(true);

    try {
      const token = localStorage.getItem('jwtToken');
      
      const response = await fetch(`${API_BASE_URL}/api/playlists/generate`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt: prompt.trim(),
          selected_genres: selectedGenres,
          tracks_per_scene: tracksPerScene,
          create_spotify_playlist: createSpotifyPlaylist,
          is_public: isPublic,
          use_personalization: usePersonalization,
        }),
      });

      if (response.status === 401) {
        localStorage.removeItem('jwtToken');
        toast.error("Session expired. Please log in again.");
        navigate('/');
        return;
      }

      if (response.status === 429) {
        toast.error("Rate limit exceeded. Please try again later.");
        setIsLoading(false);
        return;
      }

      if (response.status === 402) {
        toast.error("Payment required. Please check your account.");
        setIsLoading(false);
        return;
      }

      if (!response.ok) {
        throw new Error('Failed to generate playlist');
      }

      const data = await response.json();
      
      // Store the result and navigate to results page
      localStorage.setItem('playlistResult', JSON.stringify(data));
      navigate('/results');
    } catch (error) {
      console.error('Error generating playlist:', error);
      toast.error("Failed to generate playlist. Please try again.");
      setIsLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('jwtToken');
    toast.success("Logged out successfully");
    navigate('/');
  };

  if (isLoading) {
    return <LoadingAnimation />;
  }

  return (
    <div className="min-h-screen bg-gradient-hero">
      {/* Header */}
      <div className="border-b border-border/40 backdrop-blur-sm">
        <div className="container mx-auto px-4 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-bold bg-gradient-primary bg-clip-text text-transparent">
            Audynce
          </h1>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => navigate('/profile')}>
              <User className="w-4 h-4" />
              Profile
            </Button>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="w-4 h-4" />
              Logout
            </Button>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        <Card className="bg-gradient-card border-border/50 p-8 space-y-6 shadow-xl">
          {/* Title */}
          <div className="text-center space-y-2">
            <h2 className="text-3xl font-bold text-foreground">
              What's Your Vibe?
            </h2>
            <p className="text-muted-foreground">
              Describe your mood, moment, or feeling – we'll create your perfect soundtrack
            </p>
          </div>

          {/* Prompt Input */}
          <div className="space-y-2">
            <Label htmlFor="prompt" className="text-foreground">Your Vibe Description</Label>
            <Textarea
              id="prompt"
              placeholder="E.g., 'I'm feeling nostalgic about summer road trips' or 'Need energy for my morning workout'"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              className="min-h-[120px] bg-input border-border text-foreground placeholder:text-muted-foreground resize-none"
              maxLength={500}
            />
            <div className="text-right text-xs text-muted-foreground">
              {prompt.length}/500
            </div>
          </div>

          {/* Genre Selection */}
          <GenreSelector
            genres={genres}
            selectedGenres={selectedGenres}
            onGenreToggle={handleGenreToggle}
          />

          {/* Options */}
          <div className="space-y-4 pt-4">
            <div className="flex items-center justify-between">
              <Label htmlFor="personalization" className="text-sm text-foreground">
                Use my Spotify listening history
              </Label>
              <Switch
                id="personalization"
                checked={usePersonalization}
                onCheckedChange={setUsePersonalization}
              />
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="create-playlist" className="text-sm text-foreground">
                Create playlist on Spotify
              </Label>
              <Switch
                id="create-playlist"
                checked={createSpotifyPlaylist}
                onCheckedChange={setCreateSpotifyPlaylist}
              />
            </div>

            {createSpotifyPlaylist && (
              <div className="flex items-center justify-between pl-4 border-l-2 border-primary/30">
                <Label htmlFor="public" className="text-sm text-foreground">
                  Make playlist public
                </Label>
                <Switch
                  id="public"
                  checked={isPublic}
                  onCheckedChange={setIsPublic}
                />
              </div>
            )}
          </div>

          {/* Generate Button */}
          <Button
            variant="gradient"
            size="lg"
            onClick={handleGenerate}
            disabled={!prompt.trim() || selectedGenres.length === 0}
            className="w-full text-lg py-6 font-bold"
          >
            <Sparkles className="w-5 h-5" />
            Generate My Soundtrack
          </Button>
        </Card>
      </div>
    </div>
  );
};

export default Create;
