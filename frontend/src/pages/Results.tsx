import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ExternalLink, RotateCcw, Music2 } from "lucide-react";
import { toast } from "sonner";

interface Track {
  name: string;
  artist: string;
  image_url: string;
  spotify_url: string;
}

interface Scene {
  scene_name: string;
  scene_description: string;
  tracks: Track[];
}

interface PlaylistData {
  title: string;
  description: string;
  scenes: Scene[];
  spotify_playlist_url?: string;
}

interface PlaylistResponse {
  data: PlaylistData;
  message: string;
}

const Results = () => {
  const navigate = useNavigate();
  const [playlistData, setPlaylistData] = useState<PlaylistData | null>(null);

  useEffect(() => {
    // Check authentication
    const token = localStorage.getItem('jwtToken');
    if (!token) {
      navigate('/');
      return;
    }

    // Get playlist result from localStorage
    const result = localStorage.getItem('playlistResult');
    if (!result) {
      toast.error("No playlist found. Please generate one first.");
      navigate('/create');
      return;
    }

    try {
      const parsedResult: PlaylistResponse = JSON.parse(result);
      setPlaylistData(parsedResult.data);
    } catch (error) {
      console.error('Error parsing playlist result:', error);
      toast.error("Failed to load playlist. Please try again.");
      navigate('/create');
    }
  }, [navigate]);

  const handleStartOver = () => {
    localStorage.removeItem('playlistResult');
    navigate('/create');
  };

  if (!playlistData) {
    return null;
  }

  // Generate a gradient based on the playlist (simple hash-based color)
  const getGradientColors = (title: string) => {
    const hash = title.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    const hue1 = hash % 360;
    const hue2 = (hash + 120) % 360;
    return `linear-gradient(135deg, hsl(${hue1}, 70%, 50%), hsl(${hue2}, 70%, 50%))`;
  };

  return (
    <div className="min-h-screen bg-gradient-hero">
      {/* Header */}
      <div className="border-b border-border/40 backdrop-blur-sm">
        <div className="container mx-auto px-4 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-bold bg-gradient-primary bg-clip-text text-transparent">
            Audynce
          </h1>
          <Button variant="ghost" size="sm" onClick={handleStartOver}>
            <RotateCcw className="w-4 h-4" />
            Start Over
          </Button>
        </div>
      </div>

      {/* Main Content */}
      <div className="container mx-auto px-4 py-12 max-w-5xl">
        <div className="space-y-8 animate-fade-in">
          {/* Playlist Header */}
          <div className="text-center space-y-4">
            {/* Dynamic Album Cover */}
            <div className="mx-auto w-64 h-64 rounded-2xl shadow-2xl overflow-hidden">
              <div
                className="w-full h-full flex items-center justify-center"
                style={{ background: getGradientColors(playlistData.title) }}
              >
                <Music2 className="w-24 h-24 text-white/90" />
              </div>
            </div>

            {/* Title */}
            <h2 className="text-4xl font-bold text-foreground">
              {playlistData.title}
            </h2>

            {/* Description */}
            <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
              {playlistData.description}
            </p>

            {/* Spotify Button */}
            {playlistData.spotify_playlist_url && (
              <div className="pt-4">
                <Button
                  variant="hero"
                  size="lg"
                  asChild
                  className="text-lg px-8 py-6"
                >
                  <a
                    href={playlistData.spotify_playlist_url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <ExternalLink className="w-5 h-5" />
                    Open in Spotify
                  </a>
                </Button>
              </div>
            )}
          </div>

          {/* Scenes & Tracks */}
          <div className="space-y-8">
            {playlistData.scenes.map((scene, sceneIndex) => (
              <Card
                key={sceneIndex}
                className="bg-gradient-card border-border/50 p-6 space-y-4"
              >
                {/* Scene Header (if multiple scenes) */}
                {playlistData.scenes.length > 1 && (
                  <div className="space-y-2 pb-4 border-b border-border/30">
                    <h3 className="text-2xl font-bold text-foreground">
                      {scene.scene_name}
                    </h3>
                    <p className="text-muted-foreground">
                      {scene.scene_description}
                    </p>
                  </div>
                )}

                {/* Track List */}
                <div className="space-y-3">
                  {scene.tracks.map((track, trackIndex) => (
                    <a
                      key={trackIndex}
                      href={track.spotify_url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-4 p-3 rounded-lg bg-background/50 hover:bg-background/80 transition-all duration-200 group"
                    >
                      {/* Track Number */}
                      <div className="text-muted-foreground font-mono text-sm w-6 text-right">
                        {trackIndex + 1}
                      </div>

                      {/* Album Art */}
                      <div className="relative flex-shrink-0">
                        <img
                          src={track.image_url}
                          alt={track.name}
                          className="w-14 h-14 rounded shadow-lg"
                        />
                        <div className="absolute inset-0 bg-primary/0 group-hover:bg-primary/20 transition-colors rounded flex items-center justify-center">
                          <ExternalLink className="w-5 h-5 text-white opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                      </div>

                      {/* Track Info */}
                      <div className="flex-1 min-w-0">
                        <div className="font-semibold text-foreground truncate">
                          {track.name}
                        </div>
                        <div className="text-sm text-muted-foreground truncate">
                          {track.artist}
                        </div>
                      </div>
                    </a>
                  ))}
                </div>
              </Card>
            ))}
          </div>

          {/* Action Buttons */}
          <div className="flex justify-center gap-4 pt-8">
            <Button variant="outline" size="lg" onClick={handleStartOver}>
              <RotateCcw className="w-5 h-5" />
              Create Another Playlist
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Results;
