#define UNICODE
#define _UNICODE
#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#include <shlobj.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cwctype>

struct AppEntry { std::wstring title; std::wstring path; };
static std::vector<AppEntry> gApps;
static HWND gTaskbar=nullptr, gPanel=nullptr;
static bool gAllApps=false, gChromeInstallStarted=false, gSearchFocused=true;
static std::wstring gSearchText;
static const wchar_t* CHROME=L"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
static const wchar_t* CHROME_ALT=L"C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
static const wchar_t* CHROME_MSI=L"C:\\windows\\temp\\pocketwin-chrome.msi";

static bool Exists(const std::wstring& path) {
    DWORD value=GetFileAttributesW(path.c_str());
    return value!=INVALID_FILE_ATTRIBUTES && !(value&FILE_ATTRIBUTE_DIRECTORY);
}
static std::wstring ChromePath() {
    if(Exists(CHROME)) return CHROME;
    if(Exists(CHROME_ALT)) return CHROME_ALT;
    return L"";
}
static void AddApp(const wchar_t* title,const wchar_t* path) {
    if(Exists(path)) gApps.push_back({title,path});
}
static void LoadApps() {
    gApps.clear();
    AddApp(L"Google Chrome",CHROME);
    if(!Exists(CHROME)) AddApp(L"Google Chrome",CHROME_ALT);
    AddApp(L"File Explorer",L"C:\\windows\\explorer.exe");
    AddApp(L"Notepad",L"C:\\windows\\notepad.exe");
    AddApp(L"WordPad",L"C:\\windows\\system32\\write.exe");
    AddApp(L"Calculator",L"C:\\windows\\system32\\calc.exe");
    AddApp(L"Command Prompt",L"C:\\windows\\system32\\cmd.exe");
    AddApp(L"Task Manager",L"C:\\windows\\system32\\taskmgr.exe");
    AddApp(L"Control Panel",L"C:\\windows\\system32\\control.exe");
    AddApp(L"Registry Editor",L"C:\\windows\\regedit.exe");
    AddApp(L"Wine Configuration",L"C:\\windows\\system32\\winecfg.exe");
}
static void LaunchPath(const std::wstring& path) {
    if(!path.empty()) ShellExecuteW(nullptr,L"open",path.c_str(),nullptr,nullptr,SW_SHOWNORMAL);
}
static void OpenChromeUrl(const std::wstring& input) {
    std::wstring chrome=ChromePath();
    if(chrome.empty()) {
        if(Exists(CHROME_MSI))
            ShellExecuteW(nullptr,L"open",L"msiexec.exe",
                L"/i \"C:\\windows\\temp\\pocketwin-chrome.msi\" /qn /norestart",nullptr,SW_HIDE);
        return;
    }
    std::wstring query=input;
    if(query.empty()) query=L"https://www.google.com/";
    std::wstring lower=query;
    std::transform(lower.begin(),lower.end(),lower.begin(),[](wchar_t c){ return (wchar_t)towlower(c); });
    std::wstring url;
    if(lower.rfind(L"https://",0)==0||lower.rfind(L"http://",0)==0) url=query;
    else if(query.find(L' ')==std::wstring::npos && query.find(L'.')!=std::wstring::npos) url=L"https://"+query;
    else {
        url=L"https://www.google.com/search?q=";
        for(wchar_t ch:query) url+=(ch==L' '?L"%20":std::wstring(1,ch));
    }
    ShellExecuteW(nullptr,L"open",chrome.c_str(),url.c_str(),nullptr,SW_SHOWNORMAL);
}
static void RoundBox(HDC dc,int x,int y,int w,int h,COLORREF color,COLORREF edge,int radius=12) {
    HBRUSH brush=CreateSolidBrush(color);
    HPEN pen=CreatePen(PS_SOLID,1,edge);
    HGDIOBJ oldBrush=SelectObject(dc,brush),oldPen=SelectObject(dc,pen);
    RoundRect(dc,x,y,x+w,y+h,radius,radius);
    SelectObject(dc,oldBrush); SelectObject(dc,oldPen);
    DeleteObject(brush); DeleteObject(pen);
}
static void Text(HDC dc,const wchar_t* value,int x,int y,int w,int h,int size,COLORREF color,bool bold=false,UINT flags=DT_LEFT|DT_VCENTER|DT_SINGLELINE) {
    HFONT font=CreateFontW(-size,0,0,0,bold?FW_SEMIBOLD:FW_NORMAL,FALSE,FALSE,FALSE,DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,CLEARTYPE_QUALITY,DEFAULT_PITCH|FF_DONTCARE,L"Segoe UI");
    HGDIOBJ old=SelectObject(dc,font);
    SetBkMode(dc,TRANSPARENT); SetTextColor(dc,color);
    RECT rect={x,y,x+w,y+h}; DrawTextW(dc,value,-1,&rect,flags);
    SelectObject(dc,old); DeleteObject(font);
}
static void DrawIcon(HDC dc,const std::wstring& path,int x,int y,int size) {
    SHFILEINFOW info={};
    if(SHGetFileInfoW(path.c_str(),FILE_ATTRIBUTE_NORMAL,&info,sizeof(info),SHGFI_ICON|SHGFI_LARGEICON)) {
        DrawIconEx(dc,x,y,info.hIcon,size,size,0,nullptr,DI_NORMAL);
        DestroyIcon(info.hIcon);
    }
}
static void Logo(HDC dc,int x,int y) {
    HBRUSH b=CreateSolidBrush(RGB(70,169,245));
    for(int r=0;r<2;r++) for(int c=0;c<2;c++) {
        RECT q={x+c*10,y+r*10,x+c*10+8,y+r*10+8}; FillRect(dc,&q,b);
    }
    DeleteObject(b);
}
static void ShowPanel(bool show) {
    if(!gPanel) return;
    if(show) {
        gAllApps=false; LoadApps(); InvalidateRect(gPanel,nullptr,TRUE);
        ShowWindow(gPanel,SW_SHOWNOACTIVATE);
        SetWindowPos(gPanel,HWND_TOPMOST,0,0,0,0,SWP_NOMOVE|SWP_NOSIZE|SWP_SHOWWINDOW);
        SetForegroundWindow(gPanel);
        SetFocus(gPanel);
        gSearchFocused=true;
    } else ShowWindow(gPanel,SW_HIDE);
}
static void LaunchIndex(int i) {
    if(i<0||i>=(int)gApps.size()) return;
    std::wstring path=gApps[i].path;
    ShowPanel(false); LaunchPath(path);
}
static void DrawTaskbar(HDC dc) {
    RECT r; GetClientRect(gTaskbar,&r);
    RoundBox(dc,0,0,r.right,r.bottom,RGB(27,43,65),RGB(92,132,174),18);
    RoundBox(dc,12,9,42,43,RGB(49,75,109),RGB(95,137,183),10); Logo(dc,24,20);
    Text(dc,L"Search",58,8,62,44,11,RGB(224,237,250),false,DT_CENTER|DT_VCENTER);
    const wchar_t* paths[]={L"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",L"C:\\windows\\explorer.exe",L"C:\\windows\\notepad.exe"};
    int x=129;
    for(const wchar_t* p:paths) { DrawIcon(dc,p,x,16,28); x+=49; }
    SYSTEMTIME st; GetLocalTime(&st);
    wchar_t clock[32],date[40];
    swprintf(clock,32,L"%02u:%02u",st.wHour,st.wMinute);
    swprintf(date,40,L"%02u/%02u/%04u",st.wDay,st.wMonth,st.wYear);
    Text(dc,clock,r.right-75,5,65,25,11,RGB(244,248,255),false,DT_CENTER|DT_VCENTER);
    Text(dc,date,r.right-75,28,65,20,9,RGB(221,232,244),false,DT_CENTER|DT_VCENTER);
}
static void DrawTile(HDC dc,const AppEntry& app,int x,int y,int width,int height) {
    RoundBox(dc,x,y,width,height,RGB(37,58,86),RGB(78,111,151),10);
    DrawIcon(dc,app.path,x+12,y+12,34);
    Text(dc,app.title.c_str(),x+55,y+6,width-60,height-12,11,RGB(242,247,255),true,DT_LEFT|DT_VCENTER|DT_END_ELLIPSIS);
}
static void DrawPanel(HDC dc) {
    RECT r; GetClientRect(gPanel,&r);
    RoundBox(dc,0,0,r.right,r.bottom,RGB(25,42,68),RGB(103,145,191),22);
    Text(dc,gAllApps?L"All apps":L"Pinned",24,15,340,32,20,RGB(255,255,255),true);
    RoundBox(dc,r.right-128,14,105,30,RGB(45,70,106),RGB(86,123,166),9);
    Text(dc,gAllApps?L"Back":L"All apps  >",r.right-126,14,101,30,11,RGB(238,246,255),true,DT_CENTER|DT_VCENTER);
    if(gAllApps) {
        int y=58;
        for(int i=0;i<(int)gApps.size();i++) {
            RoundBox(dc,20,y,r.right-40,48,RGB(37,57,84),RGB(71,99,133),9);
            DrawIcon(dc,gApps[i].path,31,y+7,32);
            Text(dc,gApps[i].title.c_str(),78,y+4,r.right-98,40,12,RGB(244,248,255),true);
            y+=53;
        }
    } else {
        RoundBox(dc,20,56,r.right-40,42,RGB(41,61,89),RGB(88,124,162),10);
        Text(dc,gSearchText.empty()?L"Search the web or your apps":gSearchText.c_str(),34,57,r.right-68,40,12,RGB(218,232,248),false);
        Text(dc,L"Recommended",24,108,320,26,14,RGB(238,246,255),true);
        const int cols=3,tileW=(r.right-60)/cols,tileH=68;
        for(int i=0;i<(int)gApps.size()&&i<9;i++) {
            int row=i/cols,col=i%cols;
            DrawTile(dc,gApps[i],20+col*tileW,140+row*77,tileW-8,tileH);
        }
        Text(dc,L"PocketWin desktop",24,r.bottom-42,320,22,10,RGB(160,185,213),false);
        Text(dc,L"Power",r.right-96,r.bottom-48,70,30,11,RGB(240,247,255),true,DT_CENTER|DT_VCENTER);
    }
}
static LRESULT CALLBACK PanelProc(HWND h,UINT m,WPARAM w,LPARAM l) {
    switch(m) {
        case WM_CREATE: return 0;
        case WM_CHAR:
            if(gSearchFocused) {
                if(w==VK_RETURN) { OpenChromeUrl(gSearchText); gSearchText.clear(); InvalidateRect(h,nullptr,TRUE); return 0; }
                if(w==VK_BACK) { if(!gSearchText.empty()) gSearchText.pop_back(); }
                else if(w>=32) gSearchText.push_back((wchar_t)w);
                InvalidateRect(h,nullptr,TRUE);
                return 0;
            }
            break;
        case WM_PAINT: { PAINTSTRUCT ps; HDC dc=BeginPaint(h,&ps); DrawPanel(dc); EndPaint(h,&ps); return 0; }
        case WM_LBUTTONUP: {
            int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l); RECT r; GetClientRect(h,&r);
            if(x>r.right-138&&y<52) { gAllApps=!gAllApps; LoadApps(); InvalidateRect(h,nullptr,TRUE); return 0; }
            if(gAllApps&&y>=58) { LaunchIndex((y-58)/53); return 0; }
            if(!gAllApps&&y>=140&&y<140+3*77) {
                int tileW=(r.right-60)/3;
                int col=(x-20)/tileW,row=(y-140)/77;
                if(col>=0&&col<3&&row>=0&&row<3) LaunchIndex(row*3+col);
                return 0;
            }
            if(!gAllApps&&y>=56&&y<=100) { gSearchFocused=true; SetFocus(h); return 0; }
            if(y>r.bottom-55&&x>r.right-130) { ShowPanel(false); return 0; }
            return 0;
        }
        case WM_ERASEBKGND: return 1;
    }
    return DefWindowProcW(h,m,w,l);
}
static LRESULT CALLBACK TaskbarProc(HWND h,UINT m,WPARAM w,LPARAM l) {
    switch(m) {
        case WM_CREATE: SetTimer(h,1,2500,nullptr); return 0;
        case WM_TIMER: if(gPanel&&IsWindowVisible(gPanel)) { LoadApps(); InvalidateRect(gPanel,nullptr,TRUE); } return 0;
        case WM_PAINT: { PAINTSTRUCT ps; HDC dc=BeginPaint(h,&ps); DrawTaskbar(dc); EndPaint(h,&ps); return 0; }
        case WM_LBUTTONUP: {
            int x=GET_X_LPARAM(l);
            if(x<58) ShowPanel(!gPanel||!IsWindowVisible(gPanel));
            else if(x<122) ShowPanel(true);
            else if(x<180) OpenChromeUrl(L"https://www.google.com/");
            else if(x<235) LaunchPath(L"C:\\windows\\explorer.exe");
            else LaunchPath(L"C:\\windows\\notepad.exe");
            return 0;
        }
        case WM_ERASEBKGND: return 1;
    }
    return DefWindowProcW(h,m,w,l);
}
static HWND MakeWindow(const wchar_t* cls,const wchar_t* title,int width,int height,int bottomMargin) {
    int sw=GetSystemMetrics(SM_CXSCREEN),sh=GetSystemMetrics(SM_CYSCREEN);
    HWND hwnd=CreateWindowExW(WS_EX_LAYERED|WS_EX_TOPMOST|WS_EX_TOOLWINDOW,cls,title,WS_POPUP,
        (sw-width)/2,sh-height-bottomMargin,width,height,nullptr,nullptr,GetModuleHandleW(nullptr),nullptr);
    if(hwnd) {
        SetLayeredWindowAttributes(hwnd,0,242,LWA_ALPHA);
        SetWindowRgn(hwnd,CreateRoundRectRgn(0,0,width+1,height+1,26,26),TRUE);
    }
    return hwnd;
}
static void StartChromeInstallIfNeeded() {
    if(!ChromePath().empty()||!Exists(CHROME_MSI)||gChromeInstallStarted) return;
    gChromeInstallStarted=true;
    ShellExecuteW(nullptr,L"open",L"msiexec.exe",
        L"/i \"C:\\windows\\temp\\pocketwin-chrome.msi\" /qn /norestart",nullptr,SW_HIDE);
}
int WINAPI wWinMain(HINSTANCE instance,HINSTANCE,LPWSTR,int) {
    LoadApps();
    WNDCLASSW task={}; task.hInstance=instance; task.lpfnWndProc=TaskbarProc; task.lpszClassName=L"PocketWinNativeTaskbar";
    task.hCursor=LoadCursorW(nullptr,IDC_ARROW); task.hbrBackground=(HBRUSH)GetStockObject(NULL_BRUSH); RegisterClassW(&task);
    WNDCLASSW panel={}; panel.hInstance=instance; panel.lpfnWndProc=PanelProc; panel.lpszClassName=L"PocketWinNativeStartPanel";
    panel.hCursor=LoadCursorW(nullptr,IDC_ARROW); panel.hbrBackground=(HBRUSH)GetStockObject(NULL_BRUSH); RegisterClassW(&panel);
    gPanel=MakeWindow(L"PocketWinNativeStartPanel",L"Start",680,600,82);
    gTaskbar=MakeWindow(L"PocketWinNativeTaskbar",L"Taskbar",620,62,12);
    if(!gPanel||!gTaskbar) return 2;
    ShowWindow(gTaskbar,SW_SHOWNOACTIVATE); UpdateWindow(gTaskbar);
    StartChromeInstallIfNeeded();
    MSG msg; while(GetMessageW(&msg,nullptr,0,0)>0) { TranslateMessage(&msg); DispatchMessageW(&msg); }
    return (int)msg.wParam;
}
