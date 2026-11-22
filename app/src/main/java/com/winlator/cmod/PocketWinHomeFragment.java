package com.winlator.cmod;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contents.WineDownloader;
import com.winlator.cmod.xenvironment.ImageFs;

public class PocketWinHomeFragment extends Fragment {
    private TextView containerCountView, runtimeCountView, environmentStateView, storageView;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        Context c=requireContext();
        ScrollView scroll=new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF071426,0xFF102B4D,0xFF07182D}));
        scroll.setPadding(0,0,0,dp(c,24));
        LinearLayout page=new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(c,22),dp(c,22),dp(c,22),dp(c,18));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));

        LinearLayout top=new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=txt(c,"POCKETWIN",19,Color.WHITE,true);
        brand.setLetterSpacing(.12f);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView chip=txt(c,"LOCAL  •  ARM64  •  MALI",10,0xFFD6E8FF,true);
        chip.setPadding(dp(c,10),dp(c,7),dp(c,10),dp(c,7));
        chip.setBackground(pill(0x273C9BEF,0x6676B9F2,dp(c,24)));
        top.addView(chip);
        page.addView(top);

        TextView eyebrow=txt(c,"A WINDOWS-STYLE DESKTOP ON ANDROID",10,0xFF8FB9E5,true);
        eyebrow.setLetterSpacing(.14f);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2); ep.topMargin=dp(c,26); page.addView(eyebrow,ep);
        TextView title=txt(c,"Your Windows workspace.",30,Color.WHITE,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(c,8);page.addView(title,tp);
        TextView intro=txt(c,"Create an isolated desktop, install a compatible Wine runtime, then launch Windows software locally.",14,0xFFC1D2E7,false);
        intro.setLineSpacing(dp(c,4),1f);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(c,9);ip.bottomMargin=dp(c,22);page.addView(intro,ip);

        LinearLayout hero=glass(c,dp(c,22));
        hero.addView(tag(c,"GET STARTED"));
        TextView heroTitle=txt(c,"Build your first desktop",21,Color.WHITE,true);
        LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(-1,-2);htp.topMargin=dp(c,10);hero.addView(heroTitle,htp);
        TextView heroBody=txt(c,"Download a Wine/Proton runtime and create a container. Each container keeps its own Windows C: drive and installed programs.",13,0xFFC5D7EA,false);
        heroBody.setLineSpacing(dp(c,4),1f);
        LinearLayout.LayoutParams hbp=new LinearLayout.LayoutParams(-1,-2);hbp.topMargin=dp(c,7);hero.addView(heroBody,hbp);
        LinearLayout create=action(c,"＋","Create a container","Set up a new Windows environment",true);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(c,18);hero.addView(create,cp);
        create.setOnClickListener(v->{if(getActivity() instanceof MainActivity)((MainActivity)getActivity()).openNewContainer();});
        page.addView(hero);

        TextView section=txt(c,"YOUR ENVIRONMENT",11,0xFF9DBDE0,true);section.setLetterSpacing(.13f);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(c,24);sp.bottomMargin=dp(c,10);page.addView(section,sp);
        LinearLayout stats=glass(c,dp(c,18));stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left=stat(c,"CONTAINERS"),right=stat(c,"WINE / PROTON");
        containerCountView=(TextView)left.getChildAt(1);runtimeCountView=(TextView)right.getChildAt(1);
        stats.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        View divider=new View(c);divider.setBackgroundColor(0x33FFFFFF);stats.addView(divider,new LinearLayout.LayoutParams(dp(c,1),dp(c,48)));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(c,16);stats.addView(right,rp);page.addView(stats);
        environmentStateView=txt(c,"Checking local runtime…",12,0xFFB9D3EF,false);
        LinearLayout.LayoutParams esp=new LinearLayout.LayoutParams(-1,-2);esp.topMargin=dp(c,12);page.addView(environmentStateView,esp);

        LinearLayout components=action(c,"↓","Download components","Wine, Proton, Box64 and graphics layers",false);
        LinearLayout.LayoutParams cop=new LinearLayout.LayoutParams(-1,-2);cop.topMargin=dp(c,21);page.addView(components,cop);
        components.setOnClickListener(v->{if(getActivity() instanceof MainActivity)((MainActivity)getActivity()).navigateToSection(R.id.main_menu_contents);});
        LinearLayout containers=action(c,"▦","My containers","Open, edit, duplicate or remove desktops",false);
        LinearLayout.LayoutParams conp=new LinearLayout.LayoutParams(-1,-2);conp.topMargin=dp(c,10);page.addView(containers,conp);
        containers.setOnClickListener(v->{if(getActivity() instanceof MainActivity)((MainActivity)getActivity()).navigateToSection(R.id.main_menu_containers);});

        LinearLayout storage=glass(c,dp(c,17));storage.addView(tag(c,"LOCAL STORAGE"));
        storageView=txt(c,"Container files are stored in PocketWin app data.",12,0xFFC5D7EA,false);
        storageView.setLineSpacing(dp(c,3),1f);
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2);stp.topMargin=dp(c,7);storage.addView(storageView,stp);
        LinearLayout.LayoutParams stcp=new LinearLayout.LayoutParams(-1,-2);stcp.topMargin=dp(c,20);page.addView(storage,stcp);

        TextView note=txt(c,"Compatibility layer: Wine + Box64. This is not a licensed Windows installation, and not every Windows application will run.",11,0xFF91A9C3,false);
        note.setLineSpacing(dp(c,3),1f);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(c,17);page.addView(note,np);
        if(getActivity() instanceof androidx.appcompat.app.AppCompatActivity){
            androidx.appcompat.app.ActionBar bar=((androidx.appcompat.app.AppCompatActivity)getActivity()).getSupportActionBar();
            if(bar!=null)bar.setTitle("PocketWin");
        }
        refresh();return scroll;
    }

    @Override public void onResume(){super.onResume();refresh();}
    private void refresh(){
        if(!isAdded()||containerCountView==null)return;
        Context c=requireContext();boolean ready=ImageFs.find(c).isValid();
        int count=ready?new ContainerManager(c).getContainers().size():0;
        int runtimes=WineDownloader.getInstalledWineVersions(c).size();
        containerCountView.setText(String.valueOf(count));runtimeCountView.setText(String.valueOf(runtimes));
        environmentStateView.setText(ready?(runtimes>0?"● Runtime ready · Containers are stored locally":"● System files ready · Download a Wine/Proton runtime")
                :"● System files are still being prepared");
        environmentStateView.setTextColor(ready?0xFF9FE2C1:0xFFFFD18D);
        storageView.setText("Container prefixes and C: drives use private app storage on this tablet. Available device storage: "
                +android.text.format.Formatter.formatShortFileSize(c,c.getFilesDir().getFreeSpace())
                +". Uninstalling PocketWin removes this private data unless it is exported first.");
    }

    private LinearLayout stat(Context c,String caption){
        LinearLayout col=new LinearLayout(c);col.setOrientation(LinearLayout.VERTICAL);
        TextView cap=txt(c,caption,9,0xFF9FB9D5,true);cap.setLetterSpacing(.1f);col.addView(cap);
        TextView value=txt(c,"—",27,Color.WHITE,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,5);col.addView(value,p);return col;
    }
    private LinearLayout action(Context c,String glyph,String title,String detail,boolean primary){
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c,15),dp(c,14),dp(c,15),dp(c,14));
        row.setBackground(pill(primary?0xFF3C86D8:0x1FFFFFFF,primary?0xFF7AB8F2:0x3FFFFFFF,dp(c,16)));
        TextView icon=txt(c,glyph,24,Color.WHITE,true);icon.setGravity(Gravity.CENTER);row.addView(icon,new LinearLayout.LayoutParams(dp(c,34),dp(c,34)));
        LinearLayout labels=new LinearLayout(c);labels.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(c,13);row.addView(labels,lp);
        labels.addView(txt(c,title,15,Color.WHITE,true));
        TextView sub=txt(c,detail,11,primary?0xFFE8F3FF:0xFFB5C8DE,false);sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(c,3);labels.addView(sub,subp);
        row.addView(txt(c,"›",27,0xFFE3F0FF,false));return row;
    }
    private TextView tag(Context c,String value){TextView t=txt(c,value,10,0xFF9FC9FF,true);t.setLetterSpacing(.13f);return t;}
    private static LinearLayout glass(Context c,int radius){
        LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x2AFFFFFF,0x16FFFFFF});
        bg.setCornerRadius(radius);bg.setStroke(dp(c,1),0x3FFFFFFF);card.setBackground(bg);card.setElevation(dp(c,2));return card;
    }
    private static GradientDrawable pill(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);d.setStroke(1,stroke);return d;}
    private static TextView txt(Context c,String value,int size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);
        t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));return t;
    }
    private static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+0.5f);}
}
