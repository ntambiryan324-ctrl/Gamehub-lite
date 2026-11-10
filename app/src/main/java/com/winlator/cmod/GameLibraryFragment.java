package com.winlator.cmod;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GameLibraryFragment extends Fragment {
    private static final int BG = Color.rgb(15, 18, 24);
    private static final int CARD = Color.rgb(29, 34, 43);
    private static final int TEXT = Color.rgb(240, 243, 248);
    private static final int MUTED = Color.rgb(170, 180, 194);
    private static final int ACCENT = Color.rgb(75, 145, 255);

    private static class Game {
        final String name, category, description, source, url, action, note;
        Game(String name, String category, String description, String source, String url, String action, String note) {
            this.name=name; this.category=category; this.description=description;
            this.source=source; this.url=url; this.action=action; this.note=note;
        }
    }

    private final List<Game> games = new ArrayList<>();
    private LinearLayout list;
    private String selectedCategory = "All";
    private String query = "";

    public GameLibraryFragment() {
        games.add(new Game("Team Fortress 2","Steam","Free-to-play team shooter. Listed here as a Steam store title.","Steam Store","https://store.steampowered.com/app/440/Team_Fortress_2/","Open Steam page","Requires a Steam account and the Windows game files. Store page does not install the game into Winlator."));
        games.add(new Game("Dota 2","Steam","Free-to-play multiplayer strategy game.","Steam Store","https://store.steampowered.com/app/570/Dota_2/","Open Steam page","Large, demanding game; not a recommended first performance test on the Helio G99."));
        games.add(new Game("Aperture Desk Job","Steam","Free short game set in the Portal universe.","Steam Store","https://store.steampowered.com/app/1902490/Aperture_Desk_Job/","Open Steam page","Requires Steam and compatible Windows game files; controller-focused."));
        games.add(new Game("Xbox Cloud Gaming","Xbox","Stream supported Xbox titles from Microsoft's cloud where the service and subscription are available.","Xbox","https://www.xbox.com/play","Open Xbox Cloud Gaming","Cloud streaming is not local PC emulation. Availability, subscription and network requirements apply."));
        games.add(new Game("Xbox PC Game Pass","Xbox","Browse the official PC Game Pass catalogue and install options.","Xbox","https://www.xbox.com/xbox-game-pass/pc-game-pass","Browse PC Game Pass","Xbox console/PC Game Pass games are not directly installable by Winlator. This opens the official catalogue."));
        games.add(new Game("Xbox games catalogue","Xbox","Browse Xbox games and official platform information.","Xbox","https://www.xbox.com/games/all-games","Browse Xbox games","Xbox console games need supported Xbox hardware or cloud streaming; Winlator cannot emulate an Xbox console."));
        games.add(new Game("OpenTTD","PC","Open-source transport simulation; a lightweight candidate for an early compatibility test.","OpenTTD official site","https://www.openttd.org/downloads/openttd-releases/latest.html","Download from official site","Choose the Windows package on the official page. Some releases may need original game data files."));
        games.add(new Game("The Battle for Wesnoth","PC","Open-source turn-based fantasy strategy game.","Wesnoth official site","https://www.wesnoth.org/#download","Download from official site","Choose the Windows installer from the official download page."));
        games.add(new Game("Xonotic","PC","Open-source arena first-person shooter.","Xonotic official site","https://xonotic.org/download/","Download from official site","Choose the Windows package. Performance may be demanding on this tablet."));
        games.add(new Game("SuperTuxKart","PC","Open-source kart racing game; useful for testing 3D rendering and controls.","SuperTuxKart official site","https://supertuxkart.net/Download","Download from official site","Choose the Windows installer. It may be too demanding at higher resolutions."));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(root);

        TextView heading = text("Game Library", 24, TEXT, true);
        root.addView(heading);
        TextView intro = text("Browse games and use official sources. After downloading a Windows installer, open File Manager > Drive D: (Downloads), select the .exe file and choose Run.", 13, MUTED, false);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.topMargin = dp(6);
        root.addView(intro, introLp);

        EditText search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search games...");
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(background(CARD, 12));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.topMargin = dp(16);
        root.addView(search, searchLp);

        LinearLayout filters = new LinearLayout(requireContext());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, dp(12), 0, dp(10));
        root.addView(filters);
        for (String category : new String[]{"All","Steam","Xbox","PC"}) {
            Button b = new Button(requireContext());
            b.setText(category);
            b.setTextSize(12);
            b.setAllCaps(false);
            b.setTextColor(TEXT);
            b.setBackground(background(category.equals(selectedCategory) ? ACCENT : CARD, 18));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
            lp.rightMargin = dp(7);
            filters.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedCategory = category;
                for (int i=0; i<filters.getChildCount(); i++) {
                    Button item = (Button) filters.getChildAt(i);
                    item.setBackground(background(item.getText().toString().equals(selectedCategory) ? ACCENT : CARD, 18));
                }
                renderGames();
            });
        }

        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query=s.toString().trim().toLowerCase(Locale.ROOT); renderGames(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        renderGames();
        if (getActivity() instanceof AppCompatActivity) ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle("Game Library");
        return scroll;
    }

    private void renderGames() {
        if (list == null || !isAdded()) return;
        list.removeAllViews();
        int count=0;
        for (Game game : games) {
            if (!selectedCategory.equals("All") && !selectedCategory.equals(game.category)) continue;
            String haystack=(game.name+" "+game.description+" "+game.category).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            list.addView(createCard(game));
            count++;
        }
        if (count==0) {
            TextView empty=text("No games match this search.",14,MUTED,false);
            empty.setPadding(dp(8),dp(24),dp(8),dp(24));
            list.addView(empty);
        }
    }

    private View createCard(Game game) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(14),dp(14),dp(14));
        card.setBackground(background(CARD,14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2);
        cardLp.bottomMargin=dp(12);
        card.setLayoutParams(cardLp);

        TextView badge=text(game.category.toUpperCase(Locale.ROOT)+"  •  "+game.source,11,ACCENT,true);
        card.addView(badge);
        TextView title=text(game.name,18,TEXT,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=dp(6);
        card.addView(title,titleLp);
        TextView desc=text(game.description,13,MUTED,false);
        LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(-1,-2);
        descLp.topMargin=dp(5);
        card.addView(desc,descLp);
        TextView note=text(game.note,12,MUTED,false);
        LinearLayout.LayoutParams noteLp=new LinearLayout.LayoutParams(-1,-2);
        noteLp.topMargin=dp(8);
        card.addView(note,noteLp);

        Button open=new Button(requireContext());
        open.setText(game.action);
        open.setTextSize(12);
        open.setAllCaps(false);
        open.setTextColor(Color.WHITE);
        open.setBackground(background(ACCENT,10));
        LinearLayout.LayoutParams buttonLp=new LinearLayout.LayoutParams(-1,dp(44));
        buttonLp.topMargin=dp(12);
        card.addView(open,buttonLp);
        open.setOnClickListener(v -> openOfficialPage(game.url));
        return card;
    }

    private void openOfficialPage(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(),"Could not open the official page.",Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value,int size,int color,boolean bold) {
        TextView v=new TextView(requireContext());
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }

    private GradientDrawable background(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
}
