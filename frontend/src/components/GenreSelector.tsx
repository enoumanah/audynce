import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface GenreSelectorProps {
  genres: string[];
  selectedGenres: string[];
  onGenreToggle: (genre: string) => void;
  maxSelection?: number;
}

const GenreSelector = ({ genres, selectedGenres, onGenreToggle, maxSelection = 3 }: GenreSelectorProps) => {
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <label className="text-sm font-medium text-foreground">
          Select Genres (max {maxSelection})
        </label>
        <span className="text-xs text-muted-foreground">
          {selectedGenres.length}/{maxSelection} selected
        </span>
      </div>
      <div className="flex flex-wrap gap-2">
        {genres.map((genre) => {
          const isSelected = selectedGenres.includes(genre);
          const isDisabled = !isSelected && selectedGenres.length >= maxSelection;
          
          return (
            <Badge
              key={genre}
              variant={isSelected ? "default" : "outline"}
              className={cn(
                "cursor-pointer transition-all duration-200 text-sm px-4 py-2",
                isSelected && "bg-primary text-primary-foreground shadow-[0_0_15px_hsl(180_100%_50%/0.4)]",
                !isSelected && "hover:bg-card hover:border-primary/50",
                isDisabled && "opacity-50 cursor-not-allowed"
              )}
              onClick={() => !isDisabled && onGenreToggle(genre)}
            >
              {genre}
            </Badge>
          );
        })}
      </div>
    </div>
  );
};

export default GenreSelector;
