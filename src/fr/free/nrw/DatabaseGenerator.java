package fr.free.nrw;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.FontFactory;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.table.core.CMap;
import com.google.typography.font.sfntly.table.core.CMapTable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.lang.Character;

/**
 * Generate a database of mappings {character→suitable fonts for that character}
 *
 * Copyright (c) 2012 Nicolas Raoul
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
public class DatabaseGenerator {

    /**
     * Directory where font files are stored.
     */
    private String fontsDirectory;

    private HashMap<String, CMapTable> cmapTableCache = new HashMap<String, CMapTable>();

    /**
     * Constructor.
     */
    public DatabaseGenerator(String fontsDirectory) {
        this.fontsDirectory = fontsDirectory;
    }

    /**
     * Generate database.
     */
    public void generate() throws Exception {
        String[] fonts = new File(fontsDirectory).list();
        
        // A set of fonts is for instance KhmerOS.ttf,DroidSans.ttf
        List<List<String>> fontsSets = new ArrayList<List<String>>();
        
        // Each zone is an area of UTF-16 where adjacent characters have
        // the same set of suitable fonts.
        List<Integer> zones = new ArrayList<Integer>();
        
        // Maps each zone to a font set.
        // Same size as the "zones" list.
        // Contains for each zone the index of the suitable fonts set.
        List<Integer> mappings = new ArrayList<Integer>();
        
        // Check suitability of all fonts, for each character of UTF-16.
        List<String> previousSuitableFonts = null;
        for (int character=0; character<0x10FFFF; character++) {
            String s = new String(Character.toChars(character));
            System.out.print(character + ":" + s + ": ");
            List<String> suitableFonts = new ArrayList<String>();
            for (String font : fonts) {
                // Skip glyphs that fonts shouldn't implement anyways
                if(Character.getType(character) == Character.PRIVATE_USE)
                   break;
                if(fontHasCharacter(font, character)) {
                    System.out.print(font + Antisquare.FONTS_SEPARATOR);
                    suitableFonts.add(font);
                }
            }
            System.out.print("\n");
            if ( previousSuitableFonts == null || ! suitableFonts.equals(previousSuitableFonts)) {
                if ( ! fontsSets.contains(suitableFonts)) {
                    fontsSets.add(suitableFonts);
                }
                int suitableGroup = fontsSets.indexOf(suitableFonts);
                zones.add(new Integer(character));
                mappings.add(new Integer(suitableGroup));
                previousSuitableFonts = suitableFonts;
            }
        }
        
        // Debug log.
        System.out.println("\n=== Compressed database ===");
        for (int i=0;i<fontsSets.size(); i++) {
            System.out.println("Group " + i + " : " + fontsSets.get(i));
        }
        for (int i=0;i<zones.size(); i++) {
            System.out.println("Zone " + (int)zones.get(i) + " → Group " + mappings.get(i));
        }
        
        // Generate Java code.
        BufferedWriter java = new BufferedWriter(new FileWriter("gen/fr/free/nrw/AntisquareData.java"));
        java.write("package fr.free.nrw;\n");
        java.write("public class AntisquareData {\n");
        
        // Generate Java code for "fontsSets".
        java.write("public final static String[][] fontsSets = {\n");
        String delimitator = "";
        for (int i=0;i<fontsSets.size(); i++) {
            java.write(delimitator);
            java.write("{");
            
            String subDelimitator = "";
            for (String font : fontsSets.get(i)) {
                java.write(subDelimitator);
                java.write("\"" + font + "\"");
                subDelimitator = ", ";
            }
            
            java.write("}");
            delimitator = ",\n";
        }
        java.write("};\n");
        
        // Generate Java code for "zones".
        java.write("public final static int[] zones = {\n");
        delimitator = "";
        for (int i=0;i<zones.size(); i++) {
            java.write(delimitator);
            java.write("" + (int)zones.get(i));
            delimitator = ",\n";
        }
        java.write("};\n");
        
        // Generate Java code for "mappings".
        java.write("public final static char[] mappings = {\n");
        delimitator = "";
        for (int i=0;i<mappings.size(); i++) {
            java.write(delimitator);
            java.write("" + mappings.get(i));
            delimitator = ",\n";
        }
        java.write("};\n}\n");
        java.close();
    }

    /**
     * Check whether the given font has a particular character.
     */
    private boolean fontHasCharacter(String fontFilename, int charId) throws Exception {
        CMapTable cmapTable = null;
        if(cmapTableCache.containsKey(fontFilename)) {
            cmapTable = cmapTableCache.get(fontFilename);
        } else {
            Font[] srcFontarray = FontFactory.getInstance().loadFonts(new FileInputStream(
		    fontsDirectory + System.getProperty("file.separator") + fontFilename));
            Font font = srcFontarray[0];
            cmapTable = font.getTable(Tag.cmap);
            cmapTableCache.put(fontFilename, cmapTable);
        }
        // Use the bigger cmap table if available.
        CMap cmap = cmapTable.cmap(Font.PlatformId.Windows.value(),
                Font.WindowsEncodingId.UnicodeUCS4.value());
        if (cmap == null) 
            cmap = cmapTable.cmap(Font.PlatformId.Windows.value(),
                    Font.WindowsEncodingId.UnicodeUCS2.value());
 
        if (cmap.glyphId(charId) != 0)
            return true;
        return false;
    }

    /**
     * Launch database generation from system.
     */
    public static void main(String[] args) {
        try {
            DatabaseGenerator generator = new DatabaseGenerator("freely-distributable-fonts");
            generator.generate();
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
