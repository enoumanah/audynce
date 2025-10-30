import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { toast } from "sonner";
import { User, Music, Trash2, ArrowLeft, ExternalLink } from "lucide-react";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";

const API_BASE_URL = "https://audiance.onrender.com";

interface UserInfo {
  spotify_id: string;
  display_name: string;
  email: string;
  profile_image_url?: string;
  subscription_tier: string;
  playlists_count: number;
  last_login_at: string;
  created_at: string;
}

interface Scene {
  id: number;
  scene_number: number;
  mood: string;
  description: string;
  tracks: Track[];
}

interface Track {
  id: number;
  spotify_id: string;
  name: string;
  artist: string;
  album: string;
  image_url: string;
  preview_url?: string;
  spotify_url: string;
  duration_ms: number;
  position: number;
}

interface Playlist {
  id: number;
  title: string;
  description: string;
  original_narrative: string;
  scenes: Scene[];
  spotify_playlist_id?: string;
  spotify_playlist_url?: string;
  is_public: boolean;
  created_at: string;
  generation_time_ms: number;
}

const Profile = () => {
  const navigate = useNavigate();
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('jwtToken');
    if (!token) {
      navigate('/');
      return;
    }

    fetchUserInfo();
    fetchPlaylists();
  }, [navigate]);

  const fetchUserInfo = async () => {
    try {
      const token = localStorage.getItem('jwtToken');
      const response = await fetch(`${API_BASE_URL}/api/users/me`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (response.status === 401) {
        localStorage.removeItem('jwtToken');
        toast.error("Session expired. Please log in again.");
        navigate('/');
        return;
      }

      if (!response.ok) throw new Error('Failed to fetch user info');

      const response_data = await response.json();
      console.log('User info response:', response_data);
      
      // Extract data from the wrapper object
      if (response_data.success && response_data.data) {
        setUserInfo(response_data.data);
      } else {
        throw new Error('Invalid user info response');
      }
    } catch (error) {
      console.error('Error fetching user info:', error);
      toast.error("Failed to load user information");
    }
  };

  const fetchPlaylists = async () => {
    try {
      const token = localStorage.getItem('jwtToken');
      const response = await fetch(`${API_BASE_URL}/api/playlists`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (response.status === 401) {
        localStorage.removeItem('jwtToken');
        toast.error("Session expired. Please log in again.");
        navigate('/');
        return;
      }

      if (!response.ok) {
        console.error('Failed to fetch playlists, status:', response.status);
        throw new Error('Failed to fetch playlists');
      }

      const response_data = await response.json();
      console.log('Playlists response:', response_data);
      
      // Extract data array from the wrapper object
      if (response_data.success && Array.isArray(response_data.data)) {
        setPlaylists(response_data.data);
      } else {
        console.error('Playlists data is not an array:', response_data);
        setPlaylists([]);
        toast.error("Received invalid playlist data");
      }
    } catch (error) {
      console.error('Error fetching playlists:', error);
      toast.error("Failed to load playlists");
      setPlaylists([]); // Ensure playlists is always an array
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeletePlaylist = async (playlistId: number) => {
    try {
      const token = localStorage.getItem('jwtToken');
      const response = await fetch(`${API_BASE_URL}/api/playlists/${playlistId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (response.status === 401) {
        localStorage.removeItem('jwtToken');
        toast.error("Session expired. Please log in again.");
        navigate('/');
        return;
      }

      if (!response.ok) throw new Error('Failed to delete playlist');

      toast.success("Playlist deleted successfully");
      setPlaylists(playlists.filter(p => p.id !== playlistId));
    } catch (error) {
      console.error('Error deleting playlist:', error);
      toast.error("Failed to delete playlist");
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div className="min-h-screen bg-gradient-hero">
      {/* Header */}
      <div className="border-b border-border/40 backdrop-blur-sm">
        <div className="container mx-auto px-4 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-bold bg-gradient-primary bg-clip-text text-transparent">
            Audynce
          </h1>
          <Button variant="ghost" size="sm" onClick={() => navigate('/create')}>
            <ArrowLeft className="w-4 h-4" />
            Back to Create
          </Button>
        </div>
      </div>

      {/* Main Content */}
      <div className="container mx-auto px-4 py-12 max-w-5xl space-y-8">
        {/* User Info Card */}
        {userInfo && (
          <Card className="bg-gradient-card border-border/50 shadow-xl">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <User className="w-5 h-5" />
                Profile Information
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-4">
                {userInfo.profile_image_url && (
                  <img 
                    src={userInfo.profile_image_url} 
                    alt={userInfo.display_name}
                    className="w-16 h-16 rounded-full"
                  />
                )}
                <div>
                  <h3 className="text-xl font-bold">{userInfo.display_name}</h3>
                  <p className="text-muted-foreground">{userInfo.email}</p>
                </div>
              </div>
              <div className="text-sm">
                <span className="text-muted-foreground">Playlists Created:</span>{" "}
                <span className="font-medium">{userInfo.playlists_count}</span>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Playlists Section */}
        <Card className="bg-gradient-card border-border/50 shadow-xl">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Music className="w-5 h-5" />
              Your Playlists
            </CardTitle>
            <CardDescription>
              {playlists.length === 0
                ? "You haven't created any playlists yet"
                : `${playlists.length} playlist${playlists.length !== 1 ? 's' : ''}`}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="text-center py-8 text-muted-foreground">
                Loading playlists...
              </div>
            ) : playlists.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-muted-foreground mb-4">No playlists found</p>
                <Button onClick={() => navigate('/create')}>
                  Create Your First Playlist
                </Button>
              </div>
            ) : (
              <div className="space-y-4">
                {Array.isArray(playlists) && playlists.map((playlist) => (
                  <div
                    key={playlist.id}
                    className="border border-border/50 rounded-lg p-4 bg-card/50 hover:bg-card/80 transition-colors"
                  >
                    <div className="flex justify-between items-start gap-4">
                      <div className="flex-1 space-y-1">
                        <h4 className="font-semibold text-lg">{playlist.title}</h4>
                        <p className="text-sm text-muted-foreground">
                          {playlist.description}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          Created: {formatDate(playlist.created_at)}
                        </p>
                      </div>
                      <div className="flex gap-2">
                        {playlist.spotify_playlist_url && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => window.open(playlist.spotify_playlist_url, '_blank')}
                          >
                            <ExternalLink className="w-4 h-4" />
                            Open in Spotify
                          </Button>
                        )}
                        <AlertDialog>
                          <AlertDialogTrigger asChild>
                            <Button variant="destructive" size="sm">
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>Delete Playlist</AlertDialogTitle>
                              <AlertDialogDescription>
                                Are you sure you want to delete "{playlist.title}"? This action cannot be undone.
                              </AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>Cancel</AlertDialogCancel>
                              <AlertDialogAction
                                onClick={() => handleDeletePlaylist(playlist.id)}
                                className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                              >
                                Delete
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default Profile;
