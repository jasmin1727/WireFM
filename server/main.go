package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	qrcode "github.com/skip2/go-qrcode"
	"golang.org/x/net/webdav"
	"embed"
	"io/fs"
	"mime"
	"sync"
)

//go:embed static/*
var staticFS embed.FS

var (
	Port       = "8080"
	WebDAVPort = "8081"
)

var (
	password  string
	serveRoot string
)

type FileInfo struct {
	Name    string    `json:"name"`
	Path    string    `json:"path"`
	IsDir   bool      `json:"is_dir"`
	Size    int64     `json:"size"`
	ModTime time.Time `json:"mod_time"`
	Ext     string    `json:"ext"`
}

func getLocalIP() string {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "localhost"
	}
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() {
				continue
			}
			ip = ip.To4()
			if ip == nil {
				continue
			}
			return ip.String()
		}
	}
	return "localhost"
}

func authMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Password")

		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}

		pw := r.Header.Get("X-Password")
		if pw == "" {
			pw = r.URL.Query().Get("password")
		}

		if pw != password {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusUnauthorized)
			json.NewEncoder(w).Encode(map[string]string{"error": "Invalid password"})
			return
		}
		next(w, r)
	}
}

func resolvePath(p string) string {
	if p == "" || p == "~" {
		return serveRoot
	}
	if strings.HasPrefix(p, "~/") {
		p = filepath.Join(serveRoot, strings.TrimPrefix(p, "~/"))
	} else if strings.HasPrefix(p, "~\\") {
		p = filepath.Join(serveRoot, strings.TrimPrefix(p, "~\\"))
	}
	return filepath.Clean(p)
}

func listFiles(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Query().Get("path")
	cleanPath := resolvePath(path)

	entries, err := os.ReadDir(cleanPath)
	if err != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	var files []FileInfo
	for _, entry := range entries {
		info, err := entry.Info()
		if err != nil {
			continue
		}
		filePath := filepath.Join(cleanPath, entry.Name())
		files = append(files, FileInfo{
			Name:    entry.Name(),
			Path:    filePath,
			IsDir:   entry.IsDir(),
			Size:    info.Size(),
			ModTime: info.ModTime(),
			Ext:     strings.ToLower(filepath.Ext(entry.Name())),
		})
	}

	if files == nil {
		files = []FileInfo{}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"path":  cleanPath,
		"files": files,
		"os":    runtime.GOOS,
		"user":  os.Getenv("USER"),
		"host":  getHostname(),
	})
}

func downloadFile(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Query().Get("path")
	if path == "" {
		http.Error(w, "path required", http.StatusBadRequest)
		return
	}

	cleanPath := resolvePath(path)
	info, err := os.Stat(cleanPath)
	if err != nil {
		http.Error(w, "file not found", http.StatusNotFound)
		return
	}
	if info.IsDir() {
		http.Error(w, "cannot download directory", http.StatusBadRequest)
		return
	}

	inline := r.URL.Query().Get("inline") == "1" || r.URL.Query().Get("inline") == "true"
	if inline {
		w.Header().Set("Content-Disposition", "inline; filename="+filepath.Base(cleanPath))
		ext := strings.ToLower(filepath.Ext(cleanPath))
		ctype := mime.TypeByExtension(ext)
		if ctype != "" {
			w.Header().Set("Content-Type", ctype)
		}
	} else {
		w.Header().Set("Content-Disposition", "attachment; filename="+filepath.Base(cleanPath))
		w.Header().Set("Content-Type", "application/octet-stream")
	}
	http.ServeFile(w, r, cleanPath)
}

func uploadFile(w http.ResponseWriter, r *http.Request) {
	destPath := r.URL.Query().Get("path")
	cleanDestDir := resolvePath(destPath)

	err := r.ParseMultipartForm(1024 << 20) // 1GB max
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	var uploaded []string

	if r.MultipartForm != nil && r.MultipartForm.File != nil {
		for _, fileHeaders := range r.MultipartForm.File {
			for _, fileHeader := range fileHeaders {
				file, err := fileHeader.Open()
				if err != nil {
					continue
				}
				destFile := filepath.Join(cleanDestDir, filepath.Base(fileHeader.Filename))
				out, err := os.Create(destFile)
				if err != nil {
					file.Close()
					continue
				}
				io.Copy(out, file)
				out.Close()
				file.Close()
				uploaded = append(uploaded, destFile)
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	if len(uploaded) == 0 {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{"error": "No file uploaded"})
		return
	}
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": "Uploaded",
		"count":   len(uploaded),
		"files":   uploaded,
	})
}

func deleteFile(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Query().Get("path")
	if path == "" {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{"error": "path required"})
		return
	}

	cleanPath := resolvePath(path)
	err := os.RemoveAll(cleanPath)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"success": "Deleted"})
}

func copyFile(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Src  string `json:"src"`
		Dest string `json:"dest"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	src := resolvePath(req.Src)
	dest := resolvePath(req.Dest)

	srcInfo, err := os.Stat(src)
	if err != nil {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"error": "Source not found"})
		return
	}

	if srcInfo.IsDir() {
		err = copyDir(src, dest)
	} else {
		err = copyFileData(src, dest)
	}

	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"success": "Copied"})
}

func moveFile(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Src  string `json:"src"`
		Dest string `json:"dest"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	src := resolvePath(req.Src)
	dest := resolvePath(req.Dest)

	err := os.Rename(src, dest)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"success": "Moved"})
}

func createFolder(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Path string `json:"path"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	cleanPath := resolvePath(req.Path)
	err := os.MkdirAll(cleanPath, 0755)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"success": "Folder created"})
}

func verifyAuth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"authenticated": true,
		"user":          os.Getenv("USER"),
		"hostname":      getHostname(),
		"root":          serveRoot,
		"os":            runtime.GOOS,
		"version":       "1.0.0",
	})
}

type PhoneFile struct {
	Name    string `json:"name"`
	Path    string `json:"path"`
	IsDir   bool   `json:"is_dir"`
	Size    int64  `json:"size"`
	Ext     string `json:"ext"`
	ModTime string `json:"mod_time,omitempty"`
}

type PhoneState struct {
	Connected   bool        `json:"connected"`
	DeviceName  string      `json:"device_name"`
	IP          string      `json:"ip"`
	LastSeen    time.Time   `json:"last_seen"`
	CurrentPath string      `json:"current_path"`
	Files       []PhoneFile `json:"files"`
}

var (
	phoneMu    sync.Mutex
	phoneState PhoneState
)

func phoneHeartbeat(w http.ResponseWriter, r *http.Request) {
	var req struct {
		DeviceName  string      `json:"device_name"`
		CurrentPath string      `json:"current_path"`
		Files       []PhoneFile `json:"files"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	phoneMu.Lock()
	phoneState.Connected = true
	phoneState.DeviceName = req.DeviceName
	phoneState.IP = r.RemoteAddr
	phoneState.LastSeen = time.Now()
	phoneState.CurrentPath = req.CurrentPath
	phoneState.Files = req.Files
	phoneMu.Unlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"status": "ok", "connected": true})
}

func phoneStatus(w http.ResponseWriter, r *http.Request) {
	phoneMu.Lock()
	defer phoneMu.Unlock()

	if time.Since(phoneState.LastSeen) > 35*time.Second {
		phoneState.Connected = false
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(phoneState)
}

func serverInfo(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"os":       runtime.GOOS,
		"user":     os.Getenv("USER"),
		"hostname": getHostname(),
		"root":     serveRoot,
		"version":  "1.0.0",
	})
}

func getHostname() string {
	h, err := os.Hostname()
	if err != nil {
		return "unknown"
	}
	return h
}

func copyFileData(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()

	_, err = io.Copy(out, in)
	return err
}

func copyDir(src, dst string) error {
	return filepath.Walk(src, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		relPath, _ := filepath.Rel(src, path)
		destPath := filepath.Join(dst, relPath)
		if info.IsDir() {
			return os.MkdirAll(destPath, info.Mode())
		}
		return copyFileData(path, destPath)
	})
}

func printBanner(ip string) {
	fmt.Println("\033[35m")
	fmt.Println("  ██╗    ██╗██╗██████╗ ███████╗███████╗███╗   ███╗")
	fmt.Println("  ██║    ██║██║██╔══██╗██╔════╝██╔════╝████╗ ████║")
	fmt.Println("  ██║ █╗ ██║██║██████╔╝█████╗  █████╗  ██╔████╔██║")
	fmt.Println("  ██║███╗██║██║██╔══██╗██╔══╝  ██╔══╝  ██║╚██╔╝██║")
	fmt.Println("  ╚███╔███╔╝██║██║  ██║███████╗██║     ██║ ╚═╝ ██║")
	fmt.Println("   ╚══╝╚══╝ ╚═╝╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝     ╚═╝")
	fmt.Println("\033[0m")
	fmt.Println("  Wireless File Manager — Fast & Lightweight")
	fmt.Println("  ─────────────────────────────────────────")
	fmt.Printf("  Server  : http://%s:%s\n", ip, Port)
	fmt.Printf("  WebDAV  : http://%s:%s\n", ip, WebDAVPort)
	fmt.Printf("  Password: %s\n", password)
	fmt.Printf("  Root    : %s\n", serveRoot)
	fmt.Println("  ─────────────────────────────────────────")
}

func main() {
	if p := os.Getenv("PORT"); p != "" {
		Port = p
	}
	if wp := os.Getenv("WEBDAV_PORT"); wp != "" {
		WebDAVPort = wp
	}

	// Setup
	if len(os.Args) > 1 {
		serveRoot = os.Args[1]
	} else {
		home, _ := os.UserHomeDir()
		serveRoot = home
	}

	// Auto generate password
	password = fmt.Sprintf("%d", time.Now().Unix()%10000)
	if len(os.Args) > 2 {
		password = os.Args[2]
	}

	ip := getLocalIP()
	connectURL := fmt.Sprintf("http://%s:%s?password=%s", ip, Port, password)

	printBanner(ip)

	// Print QR in terminal
	fmt.Println("\n  Scan QR code with WireFM app:\n")
	q, err := qrcode.New(connectURL, qrcode.Medium)
	if err == nil {
		fmt.Println(q.ToSmallString(false))
	}
	fmt.Printf("\n  Or open: %s\n\n", connectURL)

	// REST API server
	mux := http.NewServeMux()

	// Static web UI (embedded in binary)
	subFS, err := fs.Sub(staticFS, "static")
	if err == nil {
		mux.Handle("/", http.FileServer(http.FS(subFS)))
	}

	// Info & Auth
	mux.HandleFunc("/api/info", serverInfo)
	mux.HandleFunc("/api/verify", authMiddleware(verifyAuth))

	// File operations (auth required)
	mux.HandleFunc("/api/files", authMiddleware(listFiles))
	mux.HandleFunc("/api/download", authMiddleware(downloadFile))
	mux.HandleFunc("/api/upload", authMiddleware(uploadFile))
	mux.HandleFunc("/api/delete", authMiddleware(deleteFile))
	mux.HandleFunc("/api/copy", authMiddleware(copyFile))
	mux.HandleFunc("/api/move", authMiddleware(moveFile))
	mux.HandleFunc("/api/mkdir", authMiddleware(createFolder))

	// Mobile Phone Integration
	mux.HandleFunc("/api/phone/heartbeat", authMiddleware(phoneHeartbeat))
	mux.HandleFunc("/api/phone/status", authMiddleware(phoneStatus))

	// WebDAV server (for native file manager mounting)
	webdavHandler := &webdav.Handler{
		Prefix:     "/webdav",
		FileSystem: webdav.Dir(serveRoot),
		LockSystem: webdav.NewMemLS(),
	}
	mux.Handle("/webdav/", webdavHandler)

	// Start REST API
	go func() {
		fmt.Printf("  ✅ REST API  → http://%s:%s/api/\n", ip, Port)
		if err := http.ListenAndServe(":"+Port, mux); err != nil {
			fmt.Println("Server error:", err)
		}
	}()

	// Start dedicated WebDAV server
	webdavMux := http.NewServeMux()
	webdavMux.Handle("/", &webdav.Handler{
		FileSystem: webdav.Dir(serveRoot),
		LockSystem: webdav.NewMemLS(),
	})

	fmt.Printf("  ✅ WebDAV    → davs://%s:%s\n", ip, WebDAVPort)
	fmt.Printf("  ✅ Mount on Linux: davfs2 http://%s:%s /mnt/wirefm\n\n", ip, WebDAVPort)
	fmt.Println("  Press Ctrl+C to stop\n")

	if err := http.ListenAndServe(":"+WebDAVPort, webdavMux); err != nil {
		fmt.Println("WebDAV error:", err)
	}
}
