package me.cryo.zombierool.client;

import java.util.*;

/** A shuffle bag: hear every available track before reshuffling. */
public final class MusicPlaylist {
    private final Map<String, List<String>> bags = new HashMap<>();
    private final Map<String,Set<String>> catalogues=new HashMap<>();
    private final Map<String, String> last = new HashMap<>();

    public String next(String preset, Collection<String> available) {
        var tracks = new TreeSet<>(available);
        if (tracks.isEmpty()) return null;
        var bag = bags.computeIfAbsent(preset, key -> new ArrayList<>());
        var previous=catalogues.put(preset,Set.copyOf(tracks));
        if(previous!=null&&!previous.equals(tracks)){bag.removeIf(id->!tracks.contains(id));for(String id:tracks)if(!previous.contains(id))bag.add(id);Collections.shuffle(bag);}
        if (bag.isEmpty()) {
            bag.addAll(tracks);
            Collections.shuffle(bag);
            int end = bag.size() - 1;
            if (end > 0 && Objects.equals(bag.get(end), last.get(preset))) Collections.swap(bag, 0, end);
        }
        String selected = bag.remove(bag.size() - 1);
        last.put(preset, selected);
        return selected;
    }

    public void clear() { bags.clear(); last.clear();catalogues.clear(); }
}
