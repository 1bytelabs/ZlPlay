package com.onebytelabs.zlplay;

/** Presentation only: project identifiers and source paths are never changed. */
public final class SongDisplay {
    private SongDisplay(){}
    public static String clean(String raw){String s=raw.replace('_',' ').replaceAll("(?i)\\.(rpp|zlplay)$","");
        s=s.replaceFirst("(?i)\\s+Tom\\s+[A-G](?:#|b)?m?\\s+BPM\\s+\\d+.*$","");
        s=s.replaceFirst("(?i)\\s*[-·]\\s*[A-G](?:#|b)?m?\\s*[-·]\\s*\\d+(?:[.,]\\d+)?\\s*bpm.*$","");
        s=s.replaceFirst("(?i)\\s*[-·]\\s*Original\\s*(?:\\([A-G](?:#|b)?m?\\))?\\s*$","");return s.replaceAll("\\s+"," ").trim();}
    public static String title(String raw){String s=clean(raw);int at=s.lastIndexOf(" - ");return at>0?s.substring(0,at):s;}
    public static String artist(String raw){String s=clean(raw);int at=s.lastIndexOf(" - ");return at>0?s.substring(at+3):"";}
    public static String flat(String value){if(value==null||value.trim().isEmpty())return "—";return value.replace("♯","#").replace("C#","Db").replace("D#","Eb").replace("F#","Gb").replace("G#","Ab").replace("A#","Bb").replace("♯","#");}
}
