import { useEffect, useState } from "react";
import { Music2, Sparkles, Search, Wand2 } from "lucide-react";

const loadingSteps = [
  { text: "Analyzing your vibe...", icon: Sparkles },
  { text: "Consulting the AI...", icon: Wand2 },
  { text: "Searching the musical cosmos...", icon: Search },
  { text: "Finding those perfect tracks...", icon: Music2 },
  { text: "Mixing your Audynce playlist...", icon: Music2 },
];

const LoadingAnimation = () => {
  const [currentStep, setCurrentStep] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentStep((prev) => (prev + 1) % loadingSteps.length);
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  const CurrentIcon = loadingSteps[currentStep].icon;

  return (
    <div className="fixed inset-0 bg-gradient-hero z-50 flex items-center justify-center">
      {/* Animated background particles */}
      <div className="absolute inset-0 overflow-hidden">
        {[...Array(30)].map((_, i) => (
          <div
            key={i}
            className="absolute w-1 h-1 bg-primary rounded-full animate-pulse"
            style={{
              left: `${Math.random() * 100}%`,
              top: `${Math.random() * 100}%`,
              animationDelay: `${Math.random() * 2}s`,
              animationDuration: `${2 + Math.random() * 2}s`,
            }}
          />
        ))}
      </div>

      {/* Main content */}
      <div className="relative z-10 text-center space-y-8 px-4">
        {/* Animated icon */}
        <div className="flex justify-center">
          <div className="relative">
            <CurrentIcon className="w-24 h-24 text-primary animate-pulse-glow" />
            <div className="absolute inset-0 bg-primary/30 blur-3xl rounded-full animate-ping" />
          </div>
        </div>

        {/* Loading text with fade transition */}
        <div className="h-16 flex items-center justify-center">
          <p
            key={currentStep}
            className="text-2xl md:text-3xl font-bold text-foreground animate-fade-in"
          >
            {loadingSteps[currentStep].text}
          </p>
        </div>

        {/* Progress dots */}
        <div className="flex justify-center gap-2">
          {loadingSteps.map((_, index) => (
            <div
              key={index}
              className={cn(
                "w-2 h-2 rounded-full transition-all duration-300",
                index === currentStep
                  ? "bg-primary w-8 shadow-[0_0_10px_hsl(180_100%_50%/0.6)]"
                  : "bg-muted"
              )}
            />
          ))}
        </div>

        {/* Sound wave visualization */}
        <div className="flex items-end justify-center gap-1 h-20">
          {[...Array(12)].map((_, i) => (
            <div
              key={i}
              className="w-1.5 bg-gradient-primary rounded-full animate-wave"
              style={{
                height: `${20 + Math.random() * 60}px`,
                animationDelay: `${i * 0.1}s`,
                animationDuration: `${0.8 + Math.random() * 0.4}s`,
              }}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

function cn(...classes: (string | undefined | false)[]) {
  return classes.filter(Boolean).join(" ");
}

export default LoadingAnimation;
