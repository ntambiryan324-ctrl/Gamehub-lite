#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#include <shlobj.h>
#include <lmcons.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cwctype>
#include <cstdio>
#include <cctype>
using std::min;
using std::max;

struct AppEntry { std::wstring title,path; };
struct RecentEntry { std::wstring title,path; FILETIME modified; };
static HINSTANCE gInstance=nullptr;
static HWND gTaskbar=nullptr,gPanel=nullptr,gDismiss=nullptr,gSettings=nullptr;
static std::vector<AppEntry> gApps;
static std::vector<RecentEntry> gRecent;
static bool gAllApps=false,gSearchMode=false,gSearchFocused=false,gChromeInstallStarted=false;
static int gScroll=0,gHoverApp=-1,gSettingsSection=0;
static std::wstring gSearchText;
static const wchar_t* CHROME=L"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
static const wchar_t* CHROME_ALT=L"C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
static const wchar_t* CHROME_MSI=L"C:\\windows\\temp\\pocketwin-chrome.msi";
static const wchar_t* SETTINGS_APP=L"@settings";
static const wchar_t* SETTINGS_SECTIONS[]={L"System",L"Bluetooth & devices",L"Network & internet",L"Personalization",L"Apps",L"Accounts",L"Time & language",L"Gaming",L"Accessibility",L"Privacy & security",L"Windows Update"};
static COLORREF TXT=RGB(244,248,255),MUTED=RGB(180,199,222),ACCENT=RGB(74,157,239);

static bool Exists(const std::wstring& path) {
    DWORD a=GetFileAttributesW(path.c_str());
    return a!=INVALID_FILE_ATTRIBUTES && !(a&FILE_ATTRIBUTE_DIRECTORY);
}
static std::wstring ChromePath() {
    if(Exists(CHROME)) return CHROME;
    if(Exists(CHROME_ALT)) return CHROME_ALT;
    return L"";
}
static std::wstring BaseName(const std::wstring& path) {
    size_t p=path.find_last_of(L"\\/");
    std::wstring n=(p==std::wstring::npos)?path:path.substr(p+1);
    size_t dot=n.find_last_of(L'.'); if(dot!=std::wstring::npos)n.resize(dot);
    return n;
}
static bool HasApp(const std::wstring& title) {
    for(const auto& a:gApps) if(_wcsicmp(a.title.c_str(),title.c_str())==0)return true;
    return false;
}
static void AddApp(const std::wstring& title,const std::wstring& path) {
    if(title.empty()||path.empty()||HasApp(title))return;
    if(path!=SETTINGS_APP && !Exists(path) && GetFileAttributesW(path.c_str())==INVALID_FILE_ATTRIBUTES)return;
    gApps.push_back({title,path});
}
static void AddShortcutDirectory(const std::wstring& dir,int depth) {
    if(depth>2)return;
    WIN32_FIND_DATAW d={}; HANDLE f=FindFirstFileW((dir+L"\\*").c_str(),&d);
    if(f==INVALID_HANDLE_VALUE)return;
    do {
        if(d.cFileName[0]==L'.')continue;
        std::wstring p=dir+L"\\"+d.cFileName;
        if(d.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY) AddShortcutDirectory(p,depth+1);
        else {
            std::wstring low=p; std::transform(low.begin(),low.end(),low.begin(),[](wchar_t c){return (wchar_t)towlower(c);});
            if(low.size()>4&&low.substr(low.size()-4)==L".lnk") {
                std::wstring title=BaseName(d.cFileName);
                if(title.find(L"Uninstall")!=0&&title.find(L"uninstall")!=0)AddApp(title,p);
            }
        }
    } while(FindNextFileW(f,&d));
    FindClose(f);
}
static void LoadApps() {
    gApps.clear();
    std::wstring chrome=ChromePath(); if(!chrome.empty())AddApp(L"Google Chrome",chrome);
    AddApp(L"File Explorer",L"C:\\windows\\explorer.exe");
    AddApp(L"Notepad",L"C:\\windows\\notepad.exe");
    AddApp(L"WordPad",L"C:\\windows\\system32\\write.exe");
    AddApp(L"Calculator",L"C:\\windows\\system32\\calc.exe");
    AddApp(L"Command Prompt",L"C:\\windows\\system32\\cmd.exe");
    AddApp(L"Task Manager",L"C:\\windows\\system32\\taskmgr.exe");
    AddApp(L"Control Panel",L"C:\\windows\\system32\\control.exe");
    AddApp(L"Registry Editor",L"C:\\windows\\regedit.exe");
    AddApp(L"Wine Configuration",L"C:\\windows\\system32\\winecfg.exe");
    AddApp(L"Settings",SETTINGS_APP);
    wchar_t profile[MAX_PATH]={};
    if(GetEnvironmentVariableW(L"USERPROFILE",profile,MAX_PATH)>0) {
        std::wstring root=profile; AddApp(L"Downloads",root+L"\\Downloads"); AddApp(L"Documents",root+L"\\Documents");
    }
    wchar_t common[MAX_PATH]={},user[MAX_PATH]={};
    if(SHGetFolderPathW(nullptr,CSIDL_COMMON_PROGRAMS,nullptr,SHGFP_TYPE_CURRENT,common)==S_OK)AddShortcutDirectory(common,0);
    if(SHGetFolderPathW(nullptr,CSIDL_PROGRAMS,nullptr,SHGFP_TYPE_CURRENT,user)==S_OK)AddShortcutDirectory(user,0);
}
static void LoadRecent() {
    gRecent.clear(); wchar_t folder[MAX_PATH]={};
    if(SHGetFolderPathW(nullptr,CSIDL_RECENT,nullptr,SHGFP_TYPE_CURRENT,folder)!=S_OK)return;
    WIN32_FIND_DATAW d={}; HANDLE f=FindFirstFileW((std::wstring(folder)+L"\\*.lnk").c_str(),&d);
    if(f==INVALID_HANDLE_VALUE)return;
    do {
        if(d.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY)continue;
        RecentEntry r; r.path=std::wstring(folder)+L"\\"+d.cFileName;r.title=BaseName(d.cFileName);r.modified=d.ftLastWriteTime;gRecent.push_back(r);
    } while(FindNextFileW(f,&d));
    FindClose(f);
    std::sort(gRecent.begin(),gRecent.end(),[](const RecentEntry&a,const RecentEntry&b){return CompareFileTime(&a.modified,&b.modified)>0;});
    if(gRecent.size()>6)gRecent.resize(6);
}
static std::wstring UrlEncode(const std::wstring& value) {
    int n=WideCharToMultiByte(CP_UTF8,0,value.c_str(),-1,nullptr,0,nullptr,nullptr);
    if(n<=1)return value;
    std::string utf8((size_t)n,'\0');WideCharToMultiByte(CP_UTF8,0,value.c_str(),-1,&utf8[0],n,nullptr,nullptr);
    const wchar_t* hex=L"0123456789ABCDEF";std::wstring out;
    for(size_t i=0;i+1<utf8.size();i++){unsigned char c=(unsigned char)utf8[i];
        if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~')out.push_back((wchar_t)c);
        else if(c==' ')out.push_back(L'+');else{out.push_back(L'%');out.push_back(hex[c>>4]);out.push_back(hex[c&15]);}}
    return out;
}
static void HideLegacyWineTaskbar() {
    HWND legacy=FindWindowW(L"Shell_TrayWnd",nullptr);
    if(legacy) ShowWindow(legacy,SW_HIDE);
}
static void SetDefaultBrowser() {
    if(ChromePath().empty()) return;
    const wchar_t* keys[]={L"Software\\Classes\\http\\shell\\open\\command",L"Software\\Classes\\https\\shell\\open\\command"};
    std::wstring command=L"\\\""+ChromePath()+L"\\\" \\"%1\\\"";
    for(const wchar_t* path:keys) {
        HKEY key=nullptr;
        if(RegCreateKeyExW(HKEY_CURRENT_USER,path,0,nullptr,0,KEY_SET_VALUE,nullptr,&key,nullptr)==ERROR_SUCCESS) {
            RegSetValueExW(key,nullptr,0,REG_SZ,(const BYTE*)command.c_str(),(DWORD)((command.size()+1)*sizeof(wchar_t)));
            RegCloseKey(key);
        }
    }
}
static void StartChromeInstallIfNeeded() {
    if(!ChromePath().empty()||!Exists(CHROME_MSI)||gChromeInstallStarted)return;
    gChromeInstallStarted=true;
    ShellExecuteW(nullptr,L"open",L"msiexec.exe",L"/i \"C:\\windows\\temp\\pocketwin-chrome.msi\" /qn /norestart",nullptr,SW_HIDE);
}
static void OpenChromeUrl(const std::wstring& raw) {
    std::wstring chrome=ChromePath();
    if(chrome.empty()){StartChromeInstallIfNeeded();MessageBoxW(gPanel,L"Google Chrome is still being installed in this container. Open it again after setup completes.",L"Chrome setup",MB_OK|MB_ICONINFORMATION);return;}
    std::wstring input=raw.empty()?L"https://www.google.com/":raw,lower=input;
    std::transform(lower.begin(),lower.end(),lower.begin(),[](wchar_t c){return (wchar_t)towlower(c);});
    std::wstring url;
    if(lower.rfind(L"https://",0)==0||lower.rfind(L"http://",0)==0)url=input;
    else if(input.find(L' ')==std::wstring::npos&&input.find(L'.')!=std::wstring::npos)url=L"https://"+input;
    else url=L"https://www.google.com/search?q="+UrlEncode(input);
    ShellExecuteW(nullptr,L"open",chrome.c_str(),url.c_str(),nullptr,SW_SHOWNORMAL);
}
static void DrawGradient(HDC dc,const RECT&r,COLORREF top,COLORREF bottom) {
    TRIVERTEX v[2]={};
    v[0].x=r.left;v[0].y=r.top;v[0].Red=(COLOR16)(GetRValue(top)*257);v[0].Green=(COLOR16)(GetGValue(top)*257);v[0].Blue=(COLOR16)(GetBValue(top)*257);v[0].Alpha=0xFF00;
    v[1].x=r.right;v[1].y=r.bottom;v[1].Red=(COLOR16)(GetRValue(bottom)*257);v[1].Green=(COLOR16)(GetGValue(bottom)*257);v[1].Blue=(COLOR16)(GetBValue(bottom)*257);v[1].Alpha=0xFF00;
    GRADIENT_RECT g={0,1};GradientFill(dc,v,2,&g,1,GRADIENT_FILL_RECT_V);
}
static void RoundBox(HDC dc,int x,int y,int w,int h,COLORREF fill,COLORREF edge,int radius=12) {
    HBRUSH b=CreateSolidBrush(fill);HPEN p=CreatePen(PS_SOLID,1,edge);
    HGDIOBJ ob=SelectObject(dc,b),op=SelectObject(dc,p);RoundRect(dc,x,y,x+w,y+h,radius,radius);
    SelectObject(dc,ob);SelectObject(dc,op);DeleteObject(b);DeleteObject(p);
}
static void Text(HDC dc,const wchar_t* value,int x,int y,int w,int h,int size,COLORREF color,bool bold=false,UINT flags=DT_LEFT|DT_VCENTER|DT_SINGLELINE) {
    HFONT f=CreateFontW(-size,0,0,0,bold?FW_SEMIBOLD:FW_NORMAL,FALSE,FALSE,FALSE,DEFAULT_CHARSET,OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,CLEARTYPE_QUALITY,DEFAULT_PITCH|FF_DONTCARE,L"Segoe UI");
    HGDIOBJ old=SelectObject(dc,f);SetBkMode(dc,TRANSPARENT);SetTextColor(dc,color);RECT r={x,y,x+w,y+h};DrawTextW(dc,value,-1,&r,flags);SelectObject(dc,old);DeleteObject(f);
}
static void DrawLogo(HDC dc,int x,int y,int size) {
    int gap=2,side=(size-gap)/2;HBRUSH b=CreateSolidBrush(RGB(70,169,245));
    for(int r=0;r<2;r++)for(int c=0;c<2;c++){RECT q={x+c*(side+gap),y+r*(side+gap),x+c*(side+gap)+side,y+r*(side+gap)+side};FillRect(dc,&q,b);}DeleteObject(b);
}
static void DrawSearchGlyph(HDC dc,int x,int y) {
    HPEN p=CreatePen(PS_SOLID,2,RGB(220,235,250));HGDIOBJ op=SelectObject(dc,p),ob=SelectObject(dc,GetStockObject(HOLLOW_BRUSH));
    Ellipse(dc,x,y,x+17,y+17);MoveToEx(dc,x+13,y+13,nullptr);LineTo(dc,x+23,y+23);SelectObject(dc,ob);SelectObject(dc,op);DeleteObject(p);
}
static void DrawGear(HDC dc,int x,int y,int size) {Text(dc,L"\u2699",x,y,size,size,22,RGB(224,237,250),false,DT_CENTER|DT_VCENTER);}
static void DrawIcon(HDC dc,const std::wstring& path,int x,int y,int size) {
    if(path==SETTINGS_APP){DrawGear(dc,x,y,size);return;}
    SHFILEINFOW info={};
    if(SHGetFileInfoW(path.c_str(),0,&info,sizeof(info),SHGFI_ICON|SHGFI_LARGEICON)) {
        DrawIconEx(dc,x,y,info.hIcon,size,size,0,nullptr,DI_NORMAL);DestroyIcon(info.hIcon);return;
    }
    RoundBox(dc,x,y,size,size,RGB(43,93,151),RGB(115,170,221),8);
    std::wstring n=BaseName(path);std::wstring letter=n.empty()?L"?" : n.substr(0,1);
    Text(dc,letter.c_str(),x,y,size,size,16,TXT,true,DT_CENTER|DT_VCENTER);
}
static void DrawAvatar(HDC dc,int x,int y) {
    HBRUSH b=CreateSolidBrush(RGB(49,85,130));HGDIOBJ old=SelectObject(dc,b),op=SelectObject(dc,GetStockObject(NULL_PEN));Ellipse(dc,x,y,x+34,y+34);SelectObject(dc,op);SelectObject(dc,old);DeleteObject(b);
    wchar_t user[UNLEN+1]={};DWORD n=UNLEN+1;if(!GetUserNameW(user,&n))wcscpy(user,L"U");wchar_t ch[2]={user[0],0};Text(dc,ch,x,y,34,34,15,TXT,true,DT_CENTER|DT_VCENTER);
}
static HWND MakeLayered(const wchar_t* cls,const wchar_t* title,int w,int h,int x,int y,BYTE alpha) {
    HWND hwnd=CreateWindowExW(WS_EX_LAYERED|WS_EX_TOPMOST|WS_EX_TOOLWINDOW,cls,title,WS_POPUP,x,y,w,h,nullptr,nullptr,gInstance,nullptr);
    if(hwnd){SetLayeredWindowAttributes(hwnd,0,alpha,LWA_ALPHA);SetWindowRgn(hwnd,CreateRoundRectRgn(0,0,w+1,h+1,24,24),TRUE);}return hwnd;
}
static void HideStart() {if(gPanel)ShowWindow(gPanel,SW_HIDE);if(gDismiss)ShowWindow(gDismiss,SW_HIDE);gSearchFocused=false;}
static void OpenSettings();
static void LaunchPath(const std::wstring& path) {
    if(path==SETTINGS_APP){HideStart();OpenSettings();return;}
    if(path.empty())return;HideStart();ShellExecuteW(nullptr,L"open",path.c_str(),nullptr,nullptr,SW_SHOWNORMAL);
}
static void LaunchApp(int i){if(i>=0&&i<(int)gApps.size())LaunchPath(gApps[i].path);}
static void LaunchRecent(int i){if(i>=0&&i<(int)gRecent.size()){std::wstring p=gRecent[i].path;HideStart();ShellExecuteW(nullptr,L"open",p.c_str(),nullptr,nullptr,SW_SHOWNORMAL);}}
static void ShowStart(bool searchMode) {
    if(!gPanel)return;gAllApps=false;gScroll=0;gSearchMode=searchMode;gSearchText.clear();gSearchFocused=searchMode;LoadApps();LoadRecent();
    int sw=GetSystemMetrics(SM_CXSCREEN),sh=GetSystemMetrics(SM_CYSCREEN),w=min(820,sw-32),h=min(670,sh-82);
    SetWindowPos(gDismiss,HWND_TOPMOST,0,0,sw,sh,SWP_SHOWWINDOW);
    SetWindowPos(gPanel,HWND_TOPMOST,(sw-w)/2,sh-h-72,w,h,SWP_SHOWWINDOW);
    SetLayeredWindowAttributes(gPanel,0,242,LWA_ALPHA);ShowWindow(gPanel,SW_SHOWNOACTIVATE);SetForegroundWindow(gPanel);SetFocus(gPanel);InvalidateRect(gPanel,nullptr,TRUE);
}
static void DrawTile(HDC dc,const AppEntry&a,int x,int y,int w,int h,bool hover) {
    if(hover)RoundBox(dc,x+2,y+1,w-4,h-2,RGB(49,76,111),RGB(91,132,176),10);
    DrawIcon(dc,a.path,x+(w-38)/2,y+5,38);
    Text(dc,a.title.c_str(),x+2,y+45,w-4,h-46,10,TXT,false,DT_CENTER|DT_TOP|DT_SINGLELINE|DT_END_ELLIPSIS);
}
static void DrawRecent(HDC dc,const RecentEntry&a,int x,int y,int w,bool hover) {
    if(hover)RoundBox(dc,x,y,w,47,RGB(45,70,103),RGB(74,106,146),8);
    DrawIcon(dc,a.path,x+8,y+8,29);
    Text(dc,a.title.c_str(),x+48,y+4,w-56,20,10,TXT,true,DT_LEFT|DT_VCENTER|DT_END_ELLIPSIS);
    Text(dc,L"Recently opened",x+48,y+24,w-56,16,9,MUTED,false);
}
static void DrawStart(HDC dc) {
    RECT r;GetClientRect(gPanel,&r);DrawGradient(dc,r,RGB(35,50,74),RGB(20,34,57));
    HPEN p=CreatePen(PS_SOLID,1,RGB(96,132,170));HGDIOBJ op=SelectObject(dc,p),ob=SelectObject(dc,GetStockObject(NULL_BRUSH));
    RoundRect(dc,0,0,r.right-1,r.bottom-1,24,24);SelectObject(dc,ob);SelectObject(dc,op);DeleteObject(p);
    if(gSearchMode) {
        Text(dc,L"Search",27,18,340,32,20,TXT,true);
        RoundBox(dc,22,62,r.right-44,46,RGB(42,62,91),RGB(102,143,183),12);
        DrawSearchGlyph(dc,38,76);Text(dc,gSearchText.empty()?L"Type to search the web":gSearchText.c_str(),72,65,r.right-100,40,12,gSearchText.empty()?MUTED:TXT,false);
        Text(dc,L"Press Enter to search with Chrome",26,120,r.right-52,24,10,MUTED,false);return;
    }
    if(gAllApps) {
        Text(dc,L"All apps",26,17,340,32,20,TXT,true);
        RoundBox(dc,r.right-132,16,107,31,RGB(48,70,102),RGB(92,128,168),9);Text(dc,L"Back",r.right-130,16,103,31,10,TXT,true,DT_CENTER|DT_VCENTER);
        int colW=(r.right-52)/2;
        for(int i=0;i<16;i++){int index=gScroll*2+i;if(index>=(int)gApps.size())break;int row=i/2,col=i%2,x=18+col*colW,y=60+row*46;
            if(gHoverApp==index)RoundBox(dc,x,y,colW-8,44,RGB(48,72,105),RGB(91,128,170),9);
            DrawIcon(dc,gApps[index].path,x+9,y+7,30);Text(dc,gApps[index].title.c_str(),x+53,y+4,colW-68,37,10,TXT,true,DT_LEFT|DT_VCENTER|DT_END_ELLIPSIS);}
        Text(dc,L"Scroll for more apps",23,r.bottom-33,250,18,9,MUTED,false);return;
    }
    Text(dc,L"Pinned",27,18,300,31,19,TXT,true);
    RoundBox(dc,r.right-137,17,111,30,RGB(48,70,102),RGB(90,126,165),9);
    Text(dc,L"All apps  >",r.right-135,17,107,30,10,TXT,true,DT_CENTER|DT_VCENTER);
    const int cols=6,left=16,top=59,cellW=(r.right-32)/cols,cellH=79;
    for(int i=0;i<(int)gApps.size()&&i<18;i++)DrawTile(dc,gApps[i],left+(i%cols)*cellW,top+(i/cols)*cellH,cellW,cellH,gHoverApp==i);
    int recLabelY=top+3*cellH+2;
    Text(dc,L"Recommended",27,recLabelY,300,23,14,TXT,true);
    RoundBox(dc,r.right-99,recLabelY-1,73,24,RGB(46,66,96),RGB(86,119,157),8);Text(dc,L"More  >",r.right-97,recLabelY-1,69,24,9,TXT,true,DT_CENTER|DT_VCENTER);
    int recTop=recLabelY+27,recW=(r.right-42)/2;
    if(gRecent.empty())Text(dc,L"Your recent files will appear here",29,recTop+8,r.right-58,35,10,MUTED,false);
    else for(int i=0;i<(int)gRecent.size()&&i<6;i++)DrawRecent(dc,gRecent[i],20+(i%2)*recW,recTop+(i/2)*47,recW-5,gHoverApp==100+i);
    int footerY=r.bottom-54;RECT foot={1,footerY,r.right-1,r.bottom-1};HBRUSH fb=CreateSolidBrush(RGB(23,38,60));FillRect(dc,&foot,fb);DeleteObject(fb);
    HPEN lp=CreatePen(PS_SOLID,1,RGB(72,105,144));HGDIOBJ lo=SelectObject(dc,lp);MoveToEx(dc,1,footerY,nullptr);LineTo(dc,r.right-1,footerY);SelectObject(dc,lo);DeleteObject(lp);
    DrawAvatar(dc,25,footerY+9);wchar_t user[UNLEN+1]={};DWORD n=UNLEN+1;if(!GetUserNameW(user,&n))wcscpy(user,L"PocketWin User");
    Text(dc,user,69,footerY+7,220,38,11,TXT,false);
    Text(dc,L"\u2699",r.right-111,footerY+6,40,40,21,TXT,false,DT_CENTER|DT_VCENTER);
    Text(dc,L"\u23FB",r.right-63,footerY+6,40,40,20,TXT,false,DT_CENTER|DT_VCENTER);
}
static void DrawTaskbar(HDC dc) {
    RECT r;GetClientRect(gTaskbar,&r);DrawGradient(dc,r,RGB(27,43,65),RGB(16,29,47));
    HPEN lp=CreatePen(PS_SOLID,1,RGB(69,99,135));HGDIOBJ lo=SelectObject(dc,lp);MoveToEx(dc,0,0,nullptr);LineTo(dc,r.right,0);SelectObject(dc,lo);DeleteObject(lp);
    const int btn=46,gap=5,count=6,group=count*btn+(count-1)*gap,start=(r.right-group)/2,y=6;
    RoundBox(dc,start,y,btn,btn,RGB(47,70,101),RGB(91,129,169),12);DrawLogo(dc,start+14,y+13,19);
    DrawSearchGlyph(dc,start+btn+gap+11,y+12);
    int vx=start+2*(btn+gap);RoundBox(dc,vx+8,y+9,22,26,RGB(80,119,160),RGB(153,183,215),4);RoundBox(dc,vx+16,y+14,20,25,RGB(48,79,114),RGB(153,183,215),4);
    DrawIcon(dc,L"C:\\windows\\explorer.exe",start+3*(btn+gap)+8,y+8,30);
    std::wstring cp=ChromePath();if(!cp.empty())DrawIcon(dc,cp,start+4*(btn+gap)+8,y+8,30);else Text(dc,L"C",start+4*(btn+gap)+6,y+6,34,36,16,MUTED,true,DT_CENTER|DT_VCENTER);
    DrawGear(dc,start+5*(btn+gap)+8,y+7,30);
    int tx=r.right-170;Text(dc,L"\u2303",tx,y+5,22,35,14,TXT,false,DT_CENTER|DT_VCENTER);
    HPEN np=CreatePen(PS_SOLID,2,RGB(216,231,246));HGDIOBJ no=SelectObject(dc,np);Arc(dc,tx+31,y+11,tx+53,y+31,tx+31,y+11,tx+53,y+31);MoveToEx(dc,tx+35,y+23,nullptr);LineTo(dc,tx+49,y+23);SelectObject(dc,no);DeleteObject(np);
    RoundBox(dc,tx+61,y+13,24,14,RGB(20,32,49),RGB(216,231,246),3);
    HBRUSH wb=CreateSolidBrush(RGB(216,231,246));RECT battery={tx+64,y+16,tx+79,y+24},nub={tx+87,y+17,tx+90,y+23};FillRect(dc,&battery,wb);FillRect(dc,&nub,wb);DeleteObject(wb);
    SYSTEMTIME st;GetLocalTime(&st);wchar_t clock[32],date[40];wsprintfW(clock,L"%02u:%02u",st.wHour,st.wMinute);wsprintfW(date,L"%02u/%02u/%04u",st.wDay,st.wMonth,st.wYear);
    Text(dc,clock,r.right-79,y+4,72,21,11,TXT,false,DT_CENTER|DT_VCENTER);Text(dc,date,r.right-79,y+24,72,16,9,MUTED,false,DT_CENTER|DT_VCENTER);
}
static void OpenControl(const wchar_t* args){ShellExecuteW(nullptr,L"open",L"C:\\windows\\system32\\control.exe",args,nullptr,SW_SHOWNORMAL);}
static void SettingsAction(int card) {
    switch(card){case 0:OpenControl(L"desk.cpl");break;case 1:OpenControl(L"mmsys.cpl");break;case 2:MessageBoxW(gSettings,L"Notifications are managed by the Android host.",L"Settings",MB_OK|MB_ICONINFORMATION);break;case 3:OpenControl(L"powercfg.cpl");break;case 4:LaunchPath(L"C:\\windows\\explorer.exe");break;case 5:LaunchPath(L"C:\\windows\\system32\\winecfg.exe");break;default:MessageBoxW(gSettings,L"This function is not provided by Wine. Network, Bluetooth and battery services are managed by Android.",L"Settings",MB_OK|MB_ICONINFORMATION);break;}
}
static void DrawSettingsCard(HDC dc,int x,int y,int w,const wchar_t* title,const wchar_t* subtitle,const wchar_t* glyph) {
    RoundBox(dc,x,y,w,68,RGB(37,55,81),RGB(74,106,145),12);Text(dc,glyph,x+12,y+12,36,42,18,RGB(181,214,245),true,DT_CENTER|DT_VCENTER);
    Text(dc,title,x+58,y+8,w-68,23,11,TXT,true);Text(dc,subtitle,x+58,y+31,w-68,27,9,MUTED,false,DT_LEFT|DT_VCENTER|DT_END_ELLIPSIS);
}
static void DrawSettings(HDC dc) {
    RECT r;GetClientRect(gSettings,&r);DrawGradient(dc,r,RGB(28,42,63),RGB(17,30,49));
    Text(dc,L"Settings",23,9,230,32,17,TXT,true);Text(dc,L"\u2715",r.right-43,6,34,34,16,MUTED,false,DT_CENTER|DT_VCENTER);
    RECT side={1,46,242,r.bottom-1};HBRUSH sb=CreateSolidBrush(RGB(22,35,54));FillRect(dc,&side,sb);DeleteObject(sb);
    RoundBox(dc,13,56,213,35,RGB(40,58,84),RGB(76,107,146),9);Text(dc,L"Search settings",23,56,191,35,10,MUTED,false);
    int y=101;for(int i=0;i<11;i++){RECT item={10,y,231,y+34};if(i==gSettingsSection){HBRUSH hb=CreateSolidBrush(RGB(54,80,113));FillRect(dc,&item,hb);DeleteObject(hb);HPEN p=CreatePen(PS_SOLID,3,ACCENT);HGDIOBJ op=SelectObject(dc,p);MoveToEx(dc,10,y+5,nullptr);LineTo(dc,10,y+29);SelectObject(dc,op);DeleteObject(p);}
        wchar_t g[2]={(wchar_t)(L'A'+i),0};Text(dc,g,17,y+1,27,32,10,RGB(155,196,232),true,DT_CENTER|DT_VCENTER);Text(dc,SETTINGS_SECTIONS[i],50,y+1,173,32,10,i==gSettingsSection?TXT:MUTED,i==gSettingsSection,DT_LEFT|DT_VCENTER|DT_END_ELLIPSIS);y+=38;}
    int x=264,cw=r.right-x-24;Text(dc,SETTINGS_SECTIONS[gSettingsSection],x,62,cw,39,22,TXT,true);
    if(gSettingsSection==0){
        RoundBox(dc,x,108,cw,91,RGB(37,56,83),RGB(75,107,148),13);RoundBox(dc,x+14,123,77,61,RGB(15,37,74),RGB(67,114,173),10);DrawLogo(dc,x+38,143,30);
        Text(dc,L"PocketWin PC",x+108,119,cw-125,26,14,TXT,true);Text(dc,L"Wine desktop on Android ARM64",x+108,146,cw-125,18,10,MUTED,false);Text(dc,L"Windows-compatible desktop environment",x+108,166,cw-125,18,9,MUTED,false);
        int gap=12,w=(cw-gap)/2,top=214;
        DrawSettingsCard(dc,x,top,w,L"Display",L"Resolution, scaling and desktop",L"\u25A3");
        DrawSettingsCard(dc,x+w+gap,top,w,L"Sound",L"Audio output and devices",L"\u266B");
        DrawSettingsCard(dc,x,top+80,w,L"Notifications",L"Notification preferences",L"\u25CE");
        DrawSettingsCard(dc,x+w+gap,top+80,w,L"Power & battery",L"Power options",L"\u25D0");
        DrawSettingsCard(dc,x,top+160,w,L"Storage",L"Files and available space",L"\u25A4");
        DrawSettingsCard(dc,x+w+gap,top+160,w,L"Multitasking",L"Window and desktop behavior",L"\u25A6");
    } else {
        RoundBox(dc,x,113,cw,110,RGB(37,56,83),RGB(75,107,148),13);Text(dc,SETTINGS_SECTIONS[gSettingsSection],x+18,127,cw-36,26,14,TXT,true);
        const wchar_t* desc[]={L"System preferences and desktop devices.",L"Bluetooth devices are paired by Android.",L"Network connectivity is shared from Android.",L"Windows 11 Bloom wallpaper and dark glass appearance.",L"Installed Windows-compatible desktop applications.",L"Local Wine user profile and container identity.",L"Time and language are supplied by Android.",L"Graphics and input compatibility for Windows apps.",L"Accessibility options for text, contrast and input.",L"Container isolation and application permissions.",L"Windows Update is not provided by Wine. Update the Wine runtime in PocketWin Components."};
        Text(dc,desc[gSettingsSection],x+18,161,cw-36,46,10,MUTED,false,DT_LEFT|DT_TOP|DT_WORDBREAK);
        if(gSettingsSection==3){DrawSettingsCard(dc,x,241,cw,L"Desktop background",L"Windows 11 Bloom wallpaper",L"\u25A7");DrawSettingsCard(dc,x,320,cw,L"Appearance",L"Dark translucent PocketWin theme",L"\u25D0");}
        else if(gSettingsSection==4){int yy=239;for(int i=0;i<(int)gApps.size()&&i<7;i++){DrawIcon(dc,gApps[i].path,x+7,yy+6,28);Text(dc,gApps[i].title.c_str(),x+48,yy+3,cw-58,34,10,TXT,false);yy+=42;}}
        else {Text(dc,L"Open the related Wine configuration tool for compatible options.",x+8,239,cw-16,28,10,MUTED,false);DrawSettingsCard(dc,x,278,cw,L"Open settings tool",L"Launch the related Wine control panel",L"\u2699");}
    }
}
static LRESULT CALLBACK SettingsProc(HWND h,UINT m,WPARAM w,LPARAM l) {
    switch(m){
        case WM_PAINT:{PAINTSTRUCT ps;HDC dc=BeginPaint(h,&ps);DrawSettings(dc);EndPaint(h,&ps);return 0;}
        case WM_LBUTTONDOWN:{int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l);RECT r;GetClientRect(h,&r);if(y<42&&x<r.right-50){ReleaseCapture();SendMessageW(h,WM_NCLBUTTONDOWN,HTCAPTION,0);return 0;}return 0;}
        case WM_LBUTTONUP:{int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l);RECT r;GetClientRect(h,&r);if(y<42&&x>r.right-50){ShowWindow(h,SW_HIDE);return 0;}
            if(x<242&&y>=101){int i=(y-101)/38;if(i>=0&&i<11){gSettingsSection=i;InvalidateRect(h,nullptr,TRUE);}return 0;}
            if(x>=264&&gSettingsSection==0){int w=(r.right-264-24-12)/2,top=214;if(y>=top&&y<top+68)SettingsAction(x<264+w?0:1);else if(y>=top+80&&y<top+148)SettingsAction(x<264+w?2:3);else if(y>=top+160&&y<top+228)SettingsAction(x<264+w?4:5);return 0;}
            if(x>=264&&gSettingsSection==3)SettingsAction(0);else if(x>=264&&gSettingsSection==4)SettingsAction(2);else if(x>=264&&gSettingsSection==7)SettingsAction(5);else if(x>=264)SettingsAction(6);return 0;}
        case WM_KEYDOWN:if(w==VK_ESCAPE){ShowWindow(h,SW_HIDE);return 0;}break;
        case WM_ERASEBKGND:return 1;
    }return DefWindowProcW(h,m,w,l);
}
static void OpenSettings(){
    if(!gSettings){WNDCLASSW c={};c.hInstance=gInstance;c.lpfnWndProc=SettingsProc;c.lpszClassName=L"PocketWinWin11Settings";c.hCursor=LoadCursorW(nullptr,IDC_ARROW);c.hbrBackground=(HBRUSH)GetStockObject(NULL_BRUSH);RegisterClassW(&c);
        int sw=GetSystemMetrics(SM_CXSCREEN),sh=GetSystemMetrics(SM_CYSCREEN),w=min(930,sw-40),h=min(620,sh-50);
        gSettings=MakeLayered(L"PocketWinWin11Settings",L"Settings",w,h,(sw-w)/2,(sh-h)/2,244);}
    if(gSettings){ShowWindow(gSettings,SW_SHOWNORMAL);SetForegroundWindow(gSettings);InvalidateRect(gSettings,nullptr,TRUE);}
}
static LRESULT CALLBACK DismissProc(HWND h,UINT m,WPARAM w,LPARAM l){if(m==WM_LBUTTONDOWN||m==WM_RBUTTONDOWN){HideStart();return 0;}if(m==WM_ERASEBKGND)return 1;return DefWindowProcW(h,m,w,l);}
static LRESULT CALLBACK PanelProc(HWND h,UINT m,WPARAM w,LPARAM l){
    switch(m){
        case WM_PAINT:{PAINTSTRUCT ps;HDC dc=BeginPaint(h,&ps);DrawStart(dc);EndPaint(h,&ps);return 0;}
        case WM_CHAR:if(gSearchMode&&gSearchFocused){if(w==VK_RETURN){std::wstring q=gSearchText;HideStart();OpenChromeUrl(q);return 0;}if(w==VK_BACK){if(!gSearchText.empty())gSearchText.pop_back();}else if(w>=32)gSearchText.push_back((wchar_t)w);InvalidateRect(h,nullptr,TRUE);return 0;}break;
        case WM_KEYDOWN:if(w==VK_ESCAPE){HideStart();return 0;}if(gAllApps&&(w==VK_DOWN||w==VK_NEXT)){gScroll=min(gScroll+1,max(0,((int)gApps.size()+1)/2-8));InvalidateRect(h,nullptr,TRUE);return 0;}if(gAllApps&&(w==VK_UP||w==VK_PRIOR)){gScroll=max(0,gScroll-1);InvalidateRect(h,nullptr,TRUE);return 0;}break;
        case WM_MOUSEWHEEL:if(gAllApps){int d=GET_WHEEL_DELTA_WPARAM(w);gScroll=max(0,min(gScroll+(d<0?2:-2),max(0,((int)gApps.size()+1)/2-8)));InvalidateRect(h,nullptr,TRUE);}return 0;
        case WM_MOUSEMOVE:{int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l);RECT r;GetClientRect(h,&r);int old=gHoverApp;gHoverApp=-1;
            if(!gSearchMode&&!gAllApps){int cell=(r.right-32)/6;if(y>=59&&y<59+3*79){int col=(x-16)/cell,row=(y-59)/79,index=row*6+col;if(col>=0&&col<6&&row>=0&&row<3&&index<(int)gApps.size()&&index<18)gHoverApp=index;}
                else{int recY=59+3*79+29,row=(y-recY)/47,col=(x-20)/((r.right-42)/2),index=row*2+col;if(y>=recY&&row>=0&&row<3&&col>=0&&col<2&&index<(int)gRecent.size())gHoverApp=100+index;}}
            else if(gAllApps){int row=(y-60)/46,col=(x-18)/((r.right-52)/2),index=gScroll*2+row*2+col;if(y>=60&&row>=0&&row<8&&col>=0&&col<2&&index<(int)gApps.size())gHoverApp=index;}
            if(old!=gHoverApp)InvalidateRect(h,nullptr,FALSE);return 0;}
        case WM_LBUTTONUP:{int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l);RECT r;GetClientRect(h,&r);
            if(gSearchMode){if(y<116){gSearchFocused=true;SetFocus(h);}return 0;}
            if(gAllApps){if(x>r.right-145&&y<52){gAllApps=false;gScroll=0;InvalidateRect(h,nullptr,TRUE);return 0;}if(y>=60){int row=(y-60)/46,col=(x-18)/((r.right-52)/2),i=gScroll*2+row*2+col;if(row>=0&&row<8&&col>=0&&col<2)LaunchApp(i);}return 0;}
            if(x>r.right-145&&y<52){gAllApps=true;gScroll=0;LoadApps();InvalidateRect(h,nullptr,TRUE);return 0;}
            int cell=(r.right-32)/6;if(y>=59&&y<59+3*79){int col=(x-16)/cell,row=(y-59)/79,i=row*6+col;if(col>=0&&col<6&&row>=0&&row<3)LaunchApp(i);return 0;}
            int recY=59+3*79+29;if(y>=recY&&y<recY+3*47){int row=(y-recY)/47,col=(x-20)/((r.right-42)/2),i=row*2+col;if(row>=0&&row<3&&col>=0&&col<2)LaunchRecent(i);return 0;}
            if(y>r.bottom-54){if(x>r.right-150){if(x>r.right-65)MessageBoxW(h,L"Power is managed by Android. Close the container in PocketWin when finished.",L"Power",MB_OK|MB_ICONINFORMATION);else OpenSettings();}else{MessageBoxW(h,L"PocketWin local Wine user profile",L"User account",MB_OK|MB_ICONINFORMATION);}return 0;}return 0;}
        case WM_ERASEBKGND:return 1;
    }return DefWindowProcW(h,m,w,l);
}
static LRESULT CALLBACK TaskbarProc(HWND h,UINT m,WPARAM w,LPARAM l){
    switch(m){
        case WM_CREATE:SetTimer(h,1,2200,nullptr);return 0;
        case WM_TIMER:HideLegacyWineTaskbar();StartChromeInstallIfNeeded();SetDefaultBrowser();LoadApps();LoadRecent();InvalidateRect(h,nullptr,FALSE);if(gPanel&&IsWindowVisible(gPanel))InvalidateRect(gPanel,nullptr,FALSE);return 0;
        case WM_PAINT:{PAINTSTRUCT ps;HDC dc=BeginPaint(h,&ps);DrawTaskbar(dc);EndPaint(h,&ps);return 0;}
        case WM_LBUTTONUP:{int x=GET_X_LPARAM(l),sw=GetSystemMetrics(SM_CXSCREEN),start=(sw-(6*46+5*5))/2;
            if(x>=start&&x<start+46)ShowStart(false);
            else if(x>=start+51&&x<start+97)ShowStart(true);
            else if(x>=start+102&&x<start+148)keybd_event(VK_MENU,0,0,0),keybd_event(VK_TAB,0,0,0),keybd_event(VK_TAB,0,KEYEVENTF_KEYUP,0),keybd_event(VK_MENU,0,KEYEVENTF_KEYUP,0);
            else if(x>=start+153&&x<start+199)LaunchPath(L"C:\\windows\\explorer.exe");
            else if(x>=start+204&&x<start+250){if(!ChromePath().empty())OpenChromeUrl(L"https://www.google.com/");else StartChromeInstallIfNeeded();}
            else if(x>=start+255&&x<start+301)OpenSettings();
            return 0;}
        case WM_KEYDOWN:if(w==VK_LWIN||w==VK_RWIN||w==VK_APPS)ShowStart(false);return 0;
        case WM_ERASEBKGND:return 1;
    }return DefWindowProcW(h,m,w,l);
}
int WINAPI wWinMain(HINSTANCE instance,HINSTANCE,LPWSTR,int){
    gInstance=instance;LoadApps();LoadRecent();
    WNDCLASSW task={};task.hInstance=instance;task.lpfnWndProc=TaskbarProc;task.lpszClassName=L"PocketWinWin11Taskbar";task.hCursor=LoadCursorW(nullptr,IDC_ARROW);task.hbrBackground=(HBRUSH)GetStockObject(NULL_BRUSH);RegisterClassW(&task);
    WNDCLASSW panel={};panel.hInstance=instance;panel.lpfnWndProc=PanelProc;panel.lpszClassName=L"PocketWinWin11Start";panel.hCursor=LoadCursorW(nullptr,IDC_ARROW);panel.hbrBackground=(HBRUSH)GetStockObject(NULL_BRUSH);RegisterClassW(&panel);
    WNDCLASSW dismiss={};dismiss.hInstance=instance;dismiss.lpfnWndProc=DismissProc;dismiss.lpszClassName=L"PocketWinWin11Dismiss";dismiss.hCursor=LoadCursorW(nullptr,IDC_ARROW);dismiss.hbrBackground=(HBRUSH)GetStockObject(NULL_BRUSH);RegisterClassW(&dismiss);
    int sw=GetSystemMetrics(SM_CXSCREEN),sh=GetSystemMetrics(SM_CYSCREEN),pw=min(820,sw-32),ph=min(670,sh-82);
    gDismiss=MakeLayered(L"PocketWinWin11Dismiss",L"Start dismiss",sw,sh,0,0,1);
    gPanel=MakeLayered(L"PocketWinWin11Start",L"Start",pw,ph,(sw-pw)/2,sh-ph-72,242);
    gTaskbar=MakeLayered(L"PocketWinWin11Taskbar",L"Taskbar",sw,58,0,sh-58,229);
    if(!gPanel||!gTaskbar||!gDismiss)return 2;
    HideLegacyWineTaskbar();SetDefaultBrowser();ShowWindow(gTaskbar,SW_SHOWNOACTIVATE);UpdateWindow(gTaskbar);StartChromeInstallIfNeeded();
    MSG msg;while(GetMessageW(&msg,nullptr,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}return (int)msg.wParam;
}
