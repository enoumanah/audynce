import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

const AuthCallback = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const token = searchParams.get('token');
    const code = searchParams.get('code');
    
    if (token) {
      // Store JWT token (direct token from backend)
      localStorage.setItem('jwtToken', token);
      toast.success("Successfully connected to Spotify!");
      navigate('/create');
    } else if (code) {
      // Backend sent authorization code instead of token
      // Need to exchange it for a token
      const exchangeCodeForToken = async () => {
        try {
          const callbackUrl = `${window.location.origin}/auth/callback`;
          const response = await fetch(
            `https://audiance.onrender.com/login/oauth2/code/spotify?code=${code}&redirect_uri=${encodeURIComponent(callbackUrl)}`,
            {
              method: 'GET',
              credentials: 'include',
            }
          );
          
          if (response.ok) {
            // Backend should redirect or return token
            // Check if there's a token in the response
            const data = await response.json().catch(() => null);
            if (data?.token) {
              localStorage.setItem('jwtToken', data.token);
              toast.success("Successfully connected to Spotify!");
              navigate('/create');
            } else {
              // If backend redirects, it should add token to URL
              toast.error("Token exchange failed. Please try again.");
              navigate('/');
            }
          } else {
            toast.error("Authentication failed. Please try again.");
            navigate('/');
          }
        } catch (error) {
          console.error('Token exchange error:', error);
          toast.error("Authentication failed. Please try again.");
          navigate('/');
        }
      };
      
      exchangeCodeForToken();
    } else {
      toast.error("Authentication failed. Please try again.");
      navigate('/');
    }
  }, [searchParams, navigate]);

  return (
    <div className="min-h-screen bg-gradient-hero flex items-center justify-center">
      <div className="text-center space-y-4">
        <Loader2 className="w-12 h-12 text-primary animate-spin mx-auto" />
        <p className="text-foreground text-lg">Connecting to Spotify...</p>
      </div>
    </div>
  );
};

export default AuthCallback;
