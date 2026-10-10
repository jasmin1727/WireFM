let serverIP = localStorage.getItem('wirefm_ip') || (window.location.hostname || 'localhost');
let serverPort = localStorage.getItem('wirefm_port') || (window.location.port || '8080');
let serverPass = localStorage.getItem('wirefm_pass') || '';
let serverUserRoot = '';
let currentPath = '';
let currentDevice = 'pc';
let viewMode = localStorage.getItem('wirefm_view') || 'grid';
let allFiles = [];
let clipboard = null;
let clipAction = null;
let isConnected = false;
let isPhoneConnected = false;

const getBase = () => `http://${serverIP}:${serverPort}`;
const authHeaders = () => ({ 'X-Password': serverPass, 'Content-Type': 'application/json' });

// FILE TYPE ICONS
function getIcon(file) {
  if (file.is_dir) return '📁';
  const ext = file.ext || '';
  const icons = {
    '.jpg':  '🖼️', '.jpeg': '🖼️', '.png': '🖼️', '.gif': '🖼️', '.webp': '🖼️', '.svg': '🖼️',
    '.mp4':  '🎬', '.mkv':  '🎬', '.avi':  '🎬', '.mov':  '🎬', '.webm': '🎬',
    '.mp3':  '🎵', '.wav':  '🎵', '.flac': '🎵', '.ogg':  '🎵', '.m4a':  '🎵',
    '.pdf':  '📕', '.doc':  '📝', '.docx': '📝', '.txt':  '📄', '.rtf':  '📄',
    '.zip':  '🗜️', '.tar':  '🗜️', '.gz':   '🗜️', '.rar':  '🗜️', '.7z':   '🗜️',
    '.js':   '⚡', '.ts':   '⚡', '.py':   '🐍', '.go':   '🐹', '.rs':   '🦀',
    '.html': '🌐', '.css':  '🎨', '.json': '📋', '.md':   '📋', '.yaml': '📋', '.yml': '📋',
    '.sh':   '⚙️', '.bash': '⚙️', '.apk':  '📱', '.exe':  '💻', '.iso':  '💿'
  };
  return icons[ext] || '📄';
}

function formatSize(bytes) {
  if (!bytes) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + ' KB';
  if (bytes < 1024*1024*1024) return (bytes/(1024*1024)).toFixed(1) + ' MB';
  return (bytes/(1024*1024*1024)).toFixed(1) + ' GB';
}

// SIDEBAR ACTIVE STATE MANAGER
function setSidebarActive(id) {
  document.querySelectorAll('.sidebar-item').forEach(el => el.classList.remove('active'));
  const target = document.getElementById(id);
  if (target) target.classList.add('active');
}

function updateSidebarForPath(path) {
  if (currentDevice === 'phone') {
    setSidebarActive('nav-phone');
    return;
  }
  if (!serverUserRoot) {
    setSidebarActive('nav-pc');
    return;
  }

  const normPath = path.replace(/\/+$/, '');
  const root = serverUserRoot.replace(/\/+$/, '');

  if (normPath === root) {
    setSidebarActive('nav-home');
  } else if (normPath === `${root}/Downloads` || normPath.startsWith(`${root}/Downloads/`)) {
    setSidebarActive('nav-downloads');
  } else if (normPath === `${root}/Pictures` || normPath.startsWith(`${root}/Pictures/`)) {
    setSidebarActive('nav-pictures');
  } else if (normPath === `${root}/Documents` || normPath.startsWith(`${root}/Documents/`)) {
    setSidebarActive('nav-documents');
  } else if (normPath === `${root}/Videos` || normPath.startsWith(`${root}/Videos/`)) {
    setSidebarActive('nav-videos');
  } else if (normPath === `${root}/Music` || normPath.startsWith(`${root}/Music/`)) {
    setSidebarActive('nav-music');
  } else {
    setSidebarActive('nav-pc');
  }
}

// AUTH & CONNECT
async function connect(isAuto = false) {
  const ipInput = document.getElementById('serverIP').value.trim();
  const portInput = document.getElementById('serverPort').value.trim() || '8080';
  const passInput = document.getElementById('serverPass').value.trim();
  const errorBox = document.getElementById('connectError');

  serverIP = ipInput || window.location.hostname || 'localhost';
  serverPort = portInput;
  serverPass = passInput;

  errorBox.style.display = 'none';

  try {
    const res = await fetch(`${getBase()}/api/verify`, {
      headers: { 'X-Password': serverPass }
    });

    if (res.status === 401) {
      const errData = await res.json().catch(() => ({}));
      showConnect(errData.error || 'Invalid password. Check the PIN displayed in terminal or phone app.');
      setDisconnectedUI('Invalid password');
      return false;
    }

    if (!res.ok) {
      throw new Error(`Server returned ${res.status}`);
    }

    const data = await res.json();

    // Persist successful credentials
    localStorage.setItem('wirefm_ip', serverIP);
    localStorage.setItem('wirefm_port', serverPort);
    localStorage.setItem('wirefm_pass', serverPass);

    serverUserRoot = data.root || '/';
    isConnected = true;

    document.getElementById('connectModal').classList.add('hidden');
    document.getElementById('statusDot').style.background = 'var(--success)';
    document.getElementById('statusText').textContent = `Connected to ${data.user || 'host'}@${data.hostname || 'wirefm'}`;
    const connBtn = document.getElementById('connectBtn');
    connBtn.className = 'btn btn-connected';
    connBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg><span>Connected</span>';

    if (!currentPath) {
      currentPath = serverUserRoot;
    }

    loadFiles(currentPath);
    if (!isAuto) toast(`Connected to ${data.hostname}!`, 'success');
    return true;
  } catch (err) {
    setDisconnectedUI('Connection failed');
    if (!isAuto) {
      showConnect('Unable to connect to server. Ensure WireFM is running on PC.');
    }
    return false;
  }
}

function setDisconnectedUI(reason = 'Disconnected') {
  isConnected = false;
  document.getElementById('statusDot').style.background = 'var(--danger)';
  document.getElementById('statusText').textContent = reason;
  const connBtn = document.getElementById('connectBtn');
  connBtn.className = 'btn btn-ghost';
  connBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/></svg><span>Connect</span>';
}

function showConnect(errorMsg = '') {
  document.getElementById('serverIP').value = serverIP;
  document.getElementById('serverPort').value = serverPort;
  document.getElementById('serverPass').value = serverPass;

  const errorBox = document.getElementById('connectError');
  if (errorMsg) {
    errorBox.textContent = errorMsg;
    errorBox.style.display = 'block';
  } else {
    errorBox.style.display = 'none';
  }

  document.getElementById('connectModal').classList.remove('hidden');
  setTimeout(() => document.getElementById('serverPass').focus(), 100);
}

// WINDOW INITIALIZATION
window.onload = async () => {
  setView(viewMode);

  // Check URL query params for credentials
  const params = new URLSearchParams(window.location.search);
  if (params.get('password')) {
    serverPass = params.get('password');
    localStorage.setItem('wirefm_pass', serverPass);
  }
  if (params.get('ip')) {
    serverIP = params.get('ip');
    localStorage.setItem('wirefm_ip', serverIP);
  }
  if (params.get('port')) {
    serverPort = params.get('port');
    localStorage.setItem('wirefm_port', serverPort);
  }

  document.getElementById('serverIP').value = serverIP;
  document.getElementById('serverPort').value = serverPort;
  document.getElementById('serverPass').value = serverPass;

  if (serverPass) {
    await connect(true);
  } else {
    showConnect();
  }
};

// LOAD DIRECTORY FILES
async function loadFiles(path) {
  if (currentDevice !== 'pc') {
    switchDevice('pc');
  }

  const grid = document.getElementById('fileGrid');
  grid.innerHTML = '<div class="loading"><div class="spinner"></div><span style="color:var(--text-muted);font-size:13px">Loading directory...</span></div>';

  try {
    const res = await fetch(`${getBase()}/api/files?path=${encodeURIComponent(path)}`, {
      headers: { 'X-Password': serverPass }
    });

    if (res.status === 401) {
      setDisconnectedUI('Invalid password');
      showConnect('Session expired or password changed. Please enter the current password.');
      grid.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">🔒</div>
          <span>Authentication required to browse files</span>
          <button class="btn btn-purple" style="margin-top:8px" onclick="showConnect()">Enter Password</button>
        </div>
      `;
      return;
    }

    const data = await res.json();

    if (data.error) {
      grid.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">⚠️</div>
          <span>${esc(data.error)}</span>
          <button class="btn btn-purple" style="margin-top:8px" onclick="navigate('home')">Back to Home</button>
        </div>
      `;
      toast(data.error, 'error');
      return;
    }

    currentPath = data.path;
    allFiles = data.files || [];
    document.getElementById('statusInfo').textContent = `${allFiles.length} item${allFiles.length === 1 ? '' : 's'}`;

    updateBreadcrumb(currentPath);
    updateSidebarForPath(currentPath);
    renderFiles(allFiles);
  } catch (err) {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📡</div>
        <span>Could not connect to WireFM server</span>
        <button class="btn btn-purple" style="margin-top:8px" onclick="connect()">Reconnect</button>
      </div>
    `;
    setDisconnectedUI('Connection error');
  }
}

// RENDER FILES IN GRID / LIST
function renderFiles(files) {
  const grid = document.getElementById('fileGrid');
  const q = document.getElementById('searchInput').value.trim().toLowerCase();
  const filtered = q ? files.filter(f => f.name.toLowerCase().includes(q)) : files;

  if (!filtered.length) {
    if (q) {
      grid.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">🔍</div>
          <span>No matches found for "${esc(q)}"</span>
          <button class="btn btn-ghost" style="margin-top:8px" onclick="clearSearch()">Clear Search</button>
        </div>
      `;
    } else {
      grid.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📂</div>
          <span>This folder is empty</span>
          <div style="display:flex;gap:8px;margin-top:8px">
            <button class="btn btn-purple" onclick="showUpload()">Upload Files</button>
            <button class="btn btn-ghost" onclick="createFolder()">New Folder</button>
          </div>
        </div>
      `;
    }
    return;
  }

  // Sort folders first, then alphabetically
  filtered.sort((a, b) => b.is_dir - a.is_dir || a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }));

  if (viewMode === 'grid') {
    grid.className = 'files-grid';
    grid.innerHTML = filtered.map(f => `
      <div class="file-card" onclick="handleClick(event, '${escJ(f.path)}', ${f.is_dir})"
           oncontextmenu="showCtx(event, '${escJ(f.path)}', ${f.is_dir}, '${escJ(f.name)}')">
        <div class="file-icon">${getIcon(f)}</div>
        <div class="file-name" title="${esc(f.name)}">${esc(f.name)}</div>
        ${!f.is_dir ? `<div class="file-size">${formatSize(f.size)}</div>` : ''}
      </div>
    `).join('');
  } else {
    grid.className = 'files-list';
    grid.innerHTML = filtered.map(f => `
      <div class="file-list-item" onclick="handleClick(event, '${escJ(f.path)}', ${f.is_dir})"
           oncontextmenu="showCtx(event, '${escJ(f.path)}', ${f.is_dir}, '${escJ(f.name)}')">
        <div class="file-icon">${getIcon(f)}</div>
        <div class="file-list-info">
          <div class="file-list-name" title="${esc(f.name)}">${esc(f.name)}</div>
          <div class="file-list-meta">${new Date(f.mod_time).toLocaleString()}</div>
        </div>
        <div class="file-list-size">${f.is_dir ? 'Folder' : formatSize(f.size)}</div>
      </div>
    `).join('');
  }
}

// CLICK HANDLER
function handleClick(e, path, isDir) {
  if (isDir) {
    loadFiles(path);
  } else {
    const name = path.split('/').pop();
    const ext = '.' + name.split('.').pop().toLowerCase();
    const previewable = [
      '.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg', '.bmp', '.ico',
      '.mp4', '.webm', '.mkv', '.mov',
      '.mp3', '.wav', '.ogg', '.flac', '.m4a', '.aac',
      '.pdf',
      '.txt', '.md', '.json', '.js', '.ts', '.py', '.go', '.sh', '.css', '.html', '.htm',
      '.xml', '.log', '.yml', '.yaml', '.c', '.cpp', '.h', '.hpp', '.java', '.kt',
      '.rs', '.sql', '.csv', '.toml', '.ini', '.conf', '.env'
    ];
    if (previewable.includes(ext)) {
      openPreview(path, name, ext);
    } else {
      showCtx(e, path, isDir, name);
    }
  }
}

// BREADCRUMBS & NAVIGATION
function updateBreadcrumb(path) {
  const el = document.getElementById('breadcrumb');
  const btnUp = document.getElementById('btnNavUp');

  const parts = path.split('/').filter(Boolean);
  let html = `<div class="breadcrumb-item" onclick="loadFiles('/')" title="Root">📁 /</div>`;
  let built = '';

  parts.forEach((p, i) => {
    built += '/' + p;
    const b = built;
    const isLast = (i === parts.length - 1);
    html += `<span class="sep">/</span><div class="breadcrumb-item ${isLast ? 'active' : ''}" onclick="loadFiles('${escJ(b)}')">${esc(p)}</div>`;
  });

  el.innerHTML = html;
  btnUp.disabled = (parts.length === 0);
}

function navUp() {
  if (currentDevice === 'phone') {
    if (currentPhonePath && currentPhonePath !== '/storage/emulated/0' && currentPhonePath !== '/storage') {
      const parent = currentPhonePath.substring(0, currentPhonePath.lastIndexOf('/')) || '/storage/emulated/0';
      loadPhoneDir(parent);
    }
  } else {
    if (!currentPath || currentPath === '/') return;
    const parent = currentPath.substring(0, currentPath.lastIndexOf('/')) || '/';
    loadFiles(parent);
  }
}

function navigate(type) {
  if (!serverUserRoot) serverUserRoot = '/';
  const cleanRoot = serverUserRoot.replace(/\/+$/, '');

  const targets = {
    home: cleanRoot,
    downloads: `${cleanRoot}/Downloads`,
    pictures: `${cleanRoot}/Pictures`,
    documents: `${cleanRoot}/Documents`,
    videos: `${cleanRoot}/Videos`,
    music: `${cleanRoot}/Music`
  };

  const targetPath = targets[type] || cleanRoot;
  setSidebarActive('nav-' + type);
  loadFiles(targetPath);
}

// DEVICE SWITCHING & PHONE STATUS
let phonePollInterval = null;

function switchDevice(device) {
  currentDevice = device;
  const filesContainer = document.getElementById('filesContainer');
  const phoneContainer = document.getElementById('phoneContainer');
  const mainToolbar = document.getElementById('mainToolbar');

  if (phonePollInterval) {
    clearInterval(phonePollInterval);
    phonePollInterval = null;
  }

  if (device === 'pc') {
    setSidebarActive('nav-pc');
    filesContainer.style.display = 'block';
    phoneContainer.style.display = 'none';
    mainToolbar.style.display = 'flex';
    if (!currentPath) currentPath = serverUserRoot || '/';
    loadFiles(currentPath);
  } else {
    setSidebarActive('nav-phone');
    filesContainer.style.display = 'none';
    phoneContainer.style.display = 'block';
    mainToolbar.style.display = 'none';
    document.getElementById('breadcrumb').innerHTML = `<div class="breadcrumb-item active">📱 Checking Phone...</div>`;
    checkPhoneStatus();
    phonePollInterval = setInterval(() => {
      if (currentDevice === 'phone') checkPhoneStatus();
    }, 4000);
  }
}

let currentPhonePath = '/storage/emulated/0';
let allPhoneFiles = [];

async function checkPhoneStatus(manual = false) {
  try {
    const res = await fetch(`${getBase()}/api/phone/status`, {
      headers: { 'X-Password': serverPass }
    });
    if (!res.ok) throw new Error('Status fetch failed');
    const data = await res.json();

    const notConnected = document.getElementById('phoneNotConnectedView');
    const connected = document.getElementById('phoneConnectedView');

    if (!data.connected) {
      isPhoneConnected = false;
      notConnected.style.display = 'block';
      connected.style.display = 'none';
      document.getElementById('breadcrumb').innerHTML = `<div class="breadcrumb-item active">📱 Phone / Not Connected</div>`;
      document.getElementById('statusInfo').textContent = 'Phone: Not Connected';
      if (manual) toast('Mobile device is not connected yet.', 'info');
    } else {
      isPhoneConnected = true;
      notConnected.style.display = 'none';
      connected.style.display = 'block';

      const devName = data.device_name || 'Android Device';
      document.getElementById('phoneDeviceName').textContent = devName;
      document.getElementById('phoneStoragePath').textContent = currentPhonePath;

      if (!allPhoneFiles || allPhoneFiles.length === 0) {
        if (data.files && data.files.length > 0) {
          allPhoneFiles = data.files;
          renderPhoneFiles(allPhoneFiles);
        } else {
          loadPhoneDir(currentPhonePath);
        }
      }
      updatePhoneBreadcrumb(currentPhonePath);
      document.getElementById('statusInfo').textContent = `Phone: ${allPhoneFiles.length} items`;
      if (manual) toast(`Connected to ${devName}!`, 'success');
    }
  } catch (e) {
    isPhoneConnected = false;
    document.getElementById('phoneNotConnectedView').style.display = 'block';
    document.getElementById('phoneConnectedView').style.display = 'none';
  }
}

async function loadPhoneDir(path) {
  currentPhonePath = path;
  document.getElementById('phoneStoragePath').textContent = path;
  updatePhoneBreadcrumb(path);
  const grid = document.getElementById('phoneFileGrid');
  grid.innerHTML = '<div class="loading"><div class="spinner"></div><span style="color:var(--text-muted);font-size:13px">Loading phone files...</span></div>';

  try {
    const res = await fetch(`${getBase()}/api/phone/files?path=${encodeURIComponent(path)}`, {
      headers: { 'X-Password': serverPass }
    });
    if (!res.ok) throw new Error('Failed to load phone directory');
    const data = await res.json();
    allPhoneFiles = data.files || [];
    renderPhoneFiles(allPhoneFiles);
    document.getElementById('statusInfo').textContent = `Phone: ${allPhoneFiles.length} items`;
  } catch (e) {
    grid.innerHTML = `<div class="empty-state" style="grid-column: 1 / -1"><span>Could not load folder: ${esc(path)}</span></div>`;
    toast('Error reading phone folder', 'error');
  }
}

function updatePhoneBreadcrumb(path) {
  const el = document.getElementById('breadcrumb');
  const devName = document.getElementById('phoneDeviceName').textContent || 'Android Phone';
  
  let html = `<div class="breadcrumb-item" onclick="loadPhoneDir('/storage/emulated/0')" title="Phone Storage">📱 ${esc(devName)} / Storage</div>`;
  
  const subparts = path.replace(/^\/storage\/emulated\/0\/?/, '').split('/').filter(Boolean);
  let built = '/storage/emulated/0';
  subparts.forEach((p, i) => {
    built += '/' + p;
    const b = built;
    const isLast = (i === subparts.length - 1);
    html += `<span class="sep">/</span><div class="breadcrumb-item ${isLast ? 'active' : ''}" onclick="loadPhoneDir('${escJ(b)}')">${esc(p)}</div>`;
  });
  el.innerHTML = html;
}

function renderPhoneFiles(files) {
  const grid = document.getElementById('phoneFileGrid');
  if (!files || files.length === 0) {
    grid.className = 'files-grid';
    grid.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1">
        <div class="empty-icon">📂</div>
        <span>Phone folder is empty.</span>
      </div>
    `;
    return;
  }

  // Sort folders first
  files.sort((a, b) => b.is_dir - a.is_dir || a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }));

  if (viewMode === 'grid') {
    grid.className = 'files-grid';
    grid.innerHTML = files.map(f => `
      <div class="file-card" onclick="handlePhoneClick(event, '${escJ(f.path)}', ${f.is_dir}, '${escJ(f.name)}')" oncontextmenu="showCtx(event, '${escJ(f.path)}', ${f.is_dir}, '${escJ(f.name)}', 'phone')">
        <div class="file-icon">${getIcon(f)}</div>
        <div class="file-name" title="${esc(f.name)}">${esc(f.name)}</div>
        ${!f.is_dir ? `<div class="file-size">${formatSize(f.size)}</div>` : '<div class="file-size">Folder</div>'}
      </div>
    `).join('');
  } else {
    grid.className = 'files-list';
    grid.innerHTML = files.map(f => `
      <div class="file-list-item" onclick="handlePhoneClick(event, '${escJ(f.path)}', ${f.is_dir}, '${escJ(f.name)}')" oncontextmenu="showCtx(event, '${escJ(f.path)}', ${f.is_dir}, '${escJ(f.name)}', 'phone')">
        <div class="file-icon">${getIcon(f)}</div>
        <div class="file-list-info">
          <div class="file-list-name" title="${esc(f.name)}">${esc(f.name)}</div>
          <div class="file-list-meta">${f.is_dir ? 'Folder' : 'File'}</div>
        </div>
        <div class="file-list-size">${f.is_dir ? 'Folder' : formatSize(f.size)}</div>
      </div>
    `).join('');
  }
}

function handlePhoneClick(e, path, isDir, name) {
  if (isDir) {
    loadPhoneDir(path);
  } else {
    const ext = '.' + name.split('.').pop().toLowerCase();
    const previewable = [
      '.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg', '.bmp', '.ico',
      '.mp4', '.webm', '.mkv', '.mov',
      '.mp3', '.wav', '.ogg', '.flac', '.m4a', '.aac',
      '.pdf',
      '.txt', '.md', '.json', '.js', '.ts', '.py', '.go', '.sh', '.css', '.html', '.htm',
      '.xml', '.log', '.yml', '.yaml', '.c', '.cpp', '.h', '.hpp', '.java', '.kt',
      '.rs', '.sql', '.csv', '.toml', '.ini', '.conf', '.env'
    ];
    if (previewable.includes(ext)) {
      openPreview(path, name, ext, 'phone');
    } else {
      showCtx(e, path, isDir, name, 'phone');
    }
  }
}

function copyWebdavUrl() {
  const url = `http://${serverIP}:8081/webdav`;
  navigator.clipboard.writeText(url);
  toast('Copied WebDAV URL to clipboard!', 'success');
}

// SEARCH
function filterFiles() {
  const val = document.getElementById('searchInput').value.trim();
  document.getElementById('searchClear').style.display = val ? 'block' : 'none';
  if (currentDevice === 'phone') {
    const q = val.toLowerCase();
    const filtered = q ? allPhoneFiles.filter(f => f.name.toLowerCase().includes(q)) : allPhoneFiles;
    renderPhoneFiles(filtered);
  } else {
    renderFiles(allFiles);
  }
}

function clearSearch() {
  document.getElementById('searchInput').value = '';
  document.getElementById('searchClear').style.display = 'none';
  if (currentDevice === 'phone') {
    renderPhoneFiles(allPhoneFiles);
  } else {
    renderFiles(allFiles);
  }
}

// VIEW SWITCH
function setView(mode) {
  viewMode = mode;
  localStorage.setItem('wirefm_view', mode);
  document.getElementById('gridViewBtn').classList.toggle('active', mode === 'grid');
  document.getElementById('listViewBtn').classList.toggle('active', mode === 'list');
  if (currentDevice === 'phone') {
    renderPhoneFiles(allPhoneFiles);
  } else {
    renderFiles(allFiles);
  }
}

// MEDIA VIEWER PREVIEW
let currentPreviewPath = '';
let currentPreviewText = '';
let currentPreviewStreamUrl = '';

function openPreview(path, name, ext, device = 'pc') {
  currentPreviewPath = path;
  currentPreviewText = '';
  document.getElementById('previewTitle').textContent = name;
  const modal = document.getElementById('previewModal');
  const body = document.getElementById('previewBody');
  const copyBtn = document.getElementById('previewCopyBtn');
  copyBtn.style.display = 'none';
  body.innerHTML = '';
  body.style.padding = '';

  const isPhone = (device === 'phone' || path.startsWith('/storage/') || currentDevice === 'phone');
  const ep = isPhone ? '/api/phone/download' : '/api/download';
  const streamUrl = `${getBase()}${ep}?path=${encodeURIComponent(path)}&password=${encodeURIComponent(serverPass)}&inline=1`;
  currentPreviewStreamUrl = streamUrl;

  const imageExts = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.svg', '.bmp', '.ico'];
  const videoExts = ['.mp4', '.webm', '.mkv', '.mov'];
  const audioExts = ['.mp3', '.wav', '.ogg', '.flac', '.m4a', '.aac'];
  const pdfExts = ['.pdf'];
  const textExts = [
    '.txt', '.md', '.json', '.js', '.ts', '.py', '.go', '.sh', '.css', '.html', '.htm',
    '.xml', '.log', '.yml', '.yaml', '.c', '.cpp', '.h', '.hpp', '.java', '.kt',
    '.rs', '.sql', '.csv', '.toml', '.ini', '.conf', '.env'
  ];

  if (imageExts.includes(ext)) {
    body.innerHTML = `<img src="${streamUrl}" alt="${esc(name)}" style="max-width:100%;max-height:75vh;border-radius:12px;object-fit:contain;box-shadow:0 10px 30px rgba(0,0,0,0.6);" />`;
  } else if (videoExts.includes(ext)) {
    body.innerHTML = `<video controls autoplay playsinline style="max-width:100%;max-height:75vh;border-radius:12px;outline:none;background:#000;"><source src="${streamUrl}">Your browser does not support video.</video>`;
  } else if (audioExts.includes(ext)) {
    body.innerHTML = `
      <div style="display:flex;flex-direction:column;align-items:center;gap:18px;padding:32px 24px;width:100%;max-width:480px;background:var(--card);border:1px solid var(--border);border-radius:16px;">
        <div style="font-size:56px">🎵</div>
        <div style="font-size:15px;font-weight:600;text-align:center;word-break:break-word">${esc(name)}</div>
        <audio controls autoplay style="width:100%;margin-top:10px;"><source src="${streamUrl}">Your browser does not support audio.</audio>
      </div>`;
  } else if (pdfExts.includes(ext)) {
    body.style.padding = '0';
    body.innerHTML = `<iframe src="${streamUrl}" style="width:85vw;max-width:1100px;height:78vh;border:none;background:#fff;" title="${esc(name)}"></iframe>`;
  } else if (textExts.includes(ext)) {
    body.innerHTML = '<div class="loading"><div class="spinner"></div><span style="color:var(--text-muted);font-size:13px">Loading text...</span></div>';
    fetch(streamUrl)
      .then(r => r.text())
      .then(txt => {
        currentPreviewText = txt;
        copyBtn.style.display = 'inline-flex';
        body.innerHTML = `<pre style="width:100%;max-height:72vh;overflow:auto;background:#101016;padding:18px;border-radius:10px;font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:13px;color:#d8cffc;line-height:1.6;white-space:pre-wrap;word-break:break-word;border:1px solid var(--border);">${esc(txt)}</pre>`;
      })
      .catch(() => {
        body.innerHTML = '<div class="empty-state"><span>Failed to load text preview</span></div>';
      });
  } else {
    body.innerHTML = `
      <div style="display:flex;flex-direction:column;align-items:center;gap:16px;padding:32px 20px;text-align:center;">
        <div style="font-size:48px">📄</div>
        <div style="font-size:16px;font-weight:600">${esc(name)}</div>
        <p style="color:var(--text-muted);font-size:13px;max-width:360px">This file type can be opened in a new browser tab or downloaded directly.</p>
        <button class="btn btn-purple" onclick="openPreviewInTab()">Open in Browser Tab</button>
      </div>`;
  }

  modal.classList.remove('hidden');
}

function openPreviewInTab() {
  if (currentPreviewStreamUrl) {
    window.open(currentPreviewStreamUrl, '_blank');
  }
}

function closePreview() {
  const modal = document.getElementById('previewModal');
  const body = document.getElementById('previewBody');
  const media = body.querySelector('video, audio');
  if (media) {
    media.pause();
    media.src = '';
  }
  body.innerHTML = '';
  body.style.padding = '';
  modal.classList.add('hidden');
}

function downloadPreviewFile() {
  if (currentPreviewPath) {
    const isPhone = (currentPreviewPath.startsWith('/storage/') || currentDevice === 'phone');
    const ep = isPhone ? '/api/phone/download' : '/api/download';
    window.open(`${getBase()}${ep}?path=${encodeURIComponent(currentPreviewPath)}&password=${encodeURIComponent(serverPass)}`);
  }
}

function copyPreviewText() {
  if (currentPreviewText) {
    navigator.clipboard.writeText(currentPreviewText);
    toast('Copied to clipboard!', 'success');
  }
}

// CONTEXT MENU
let ctxFile = null, ctxIsDir = false, ctxName = '', ctxDevice = 'pc';

function showCtx(e, path, isDir, name, device = 'pc') {
  e.preventDefault();
  e.stopPropagation();
  ctxFile = path;
  ctxIsDir = isDir;
  ctxName = name;
  ctxDevice = device;

  const m = document.getElementById('contextMenu');
  document.getElementById('ctxOpenItem').style.display = 'flex';
  document.getElementById('ctxPreviewItem').style.display = isDir ? 'none' : 'flex';
  document.getElementById('ctxDownloadItem').style.display = isDir ? 'none' : 'flex';
  document.getElementById('ctxCopyItem').style.display = 'flex';
  document.getElementById('ctxCutItem').style.display = 'flex';
  document.getElementById('ctxRenameItem').style.display = 'flex';
  document.getElementById('ctxDeleteItem').style.display = 'flex';
  document.getElementById('ctxFileSep1').style.display = 'block';
  document.getElementById('ctxFileSep2').style.display = 'block';
  document.getElementById('ctxFileSep3').style.display = 'block';

  document.getElementById('ctxNewFolderItem').style.display = isDir ? 'flex' : 'none';
  document.getElementById('ctxUploadItem').style.display = isDir ? 'flex' : 'none';
  document.getElementById('ctxRefreshItem').style.display = 'none';

  const pasteBtn = document.getElementById('ctxPasteBtn');
  pasteBtn.style.display = isDir ? 'flex' : 'none';
  pasteBtn.style.opacity = clipboard ? '1' : '0.4';
  pasteBtn.style.pointerEvents = clipboard ? 'auto' : 'none';

  m.style.left = Math.min(e.clientX, window.innerWidth - 210) + 'px';
  m.style.top = Math.min(e.clientY, window.innerHeight - 340) + 'px';
  m.classList.add('show');
}

function showEmptyCtx(e, device = 'pc') {
  e.preventDefault();
  e.stopPropagation();
  ctxFile = null;
  ctxIsDir = false;
  ctxName = '';
  ctxDevice = device || currentDevice;

  const m = document.getElementById('contextMenu');
  document.getElementById('ctxOpenItem').style.display = 'none';
  document.getElementById('ctxPreviewItem').style.display = 'none';
  document.getElementById('ctxDownloadItem').style.display = 'none';
  document.getElementById('ctxCopyItem').style.display = 'none';
  document.getElementById('ctxCutItem').style.display = 'none';
  document.getElementById('ctxRenameItem').style.display = 'none';
  document.getElementById('ctxDeleteItem').style.display = 'none';
  document.getElementById('ctxFileSep1').style.display = 'none';
  document.getElementById('ctxFileSep3').style.display = 'none';

  document.getElementById('ctxFileSep2').style.display = 'block';
  document.getElementById('ctxNewFolderItem').style.display = 'flex';
  document.getElementById('ctxUploadItem').style.display = 'flex';
  document.getElementById('ctxRefreshItem').style.display = 'flex';

  const pasteBtn = document.getElementById('ctxPasteBtn');
  pasteBtn.style.display = 'flex';
  pasteBtn.style.opacity = clipboard ? '1' : '0.4';
  pasteBtn.style.pointerEvents = clipboard ? 'auto' : 'none';

  m.style.left = Math.min(e.clientX, window.innerWidth - 210) + 'px';
  m.style.top = Math.min(e.clientY, window.innerHeight - 240) + 'px';
  m.classList.add('show');
}

document.addEventListener('click', () => {
  document.getElementById('contextMenu')?.classList.remove('show');
});

// Empty-space right-click handlers to prevent browser context menu & show WireFM custom menu
function initDomListeners() {
  document.getElementById('filesContainer')?.addEventListener('contextmenu', (e) => {
    if (e.target.closest('.file-card') || e.target.closest('.file-list-item')) return;
    showEmptyCtx(e, 'pc');
  });

  document.getElementById('phoneContainer')?.addEventListener('contextmenu', (e) => {
    if (e.target.closest('.file-card') || e.target.closest('.file-list-item')) return;
    if (!isPhoneConnected) return;
    showEmptyCtx(e, 'phone');
  });

  document.querySelector('.file-area')?.addEventListener('contextmenu', (e) => {
    if (e.target.closest('.file-card') || e.target.closest('.file-list-item') || e.target.closest('.toolbar')) return;
    if (currentDevice === 'phone' && !isPhoneConnected) return;
    showEmptyCtx(e, currentDevice);
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initDomListeners);
} else {
  initDomListeners();
}

function ctxOpen() {
  if (ctxIsDir) {
    if (ctxDevice === 'phone') loadPhoneDir(ctxFile);
    else loadFiles(ctxFile);
  } else {
    if (ctxDevice === 'phone') {
      const ext = '.' + ctxName.split('.').pop().toLowerCase();
      openPreview(ctxFile, ctxName, ext, 'phone');
    } else {
      ctxDownload();
    }
  }
}

function ctxPreview() {
  if (ctxFile) {
    const ext = '.' + ctxName.split('.').pop().toLowerCase();
    openPreview(ctxFile, ctxName, ext, ctxDevice);
  }
}

function ctxDownload() {
  if (ctxDevice === 'phone') {
    window.open(`${getBase()}/api/phone/download?path=${encodeURIComponent(ctxFile)}&password=${encodeURIComponent(serverPass)}`);
  } else {
    window.open(`${getBase()}/api/download?path=${encodeURIComponent(ctxFile)}&password=${encodeURIComponent(serverPass)}`);
  }
}

function ctxCopy() {
  clipboard = {
    path: ctxFile,
    name: ctxName,
    isDir: ctxIsDir,
    op: 'copy',
    device: ctxDevice
  };
  clipAction = 'copy';
  updateClipboardUI();
  toast(`Copied "${ctxName}" (${ctxDevice.toUpperCase()}) to clipboard`);
}

function ctxCut() {
  clipboard = {
    path: ctxFile,
    name: ctxName,
    isDir: ctxIsDir,
    op: 'cut',
    device: ctxDevice
  };
  clipAction = 'cut';
  updateClipboardUI();
  toast(`Cut "${ctxName}" (${ctxDevice.toUpperCase()}) to clipboard`);
}

function updateClipboardUI() {
  const topPaste = document.getElementById('topPasteBtn');
  if (clipboard) {
    topPaste.style.display = 'inline-flex';
    topPaste.classList.add('btn-paste-active');
  } else {
    topPaste.style.display = 'none';
    topPaste.classList.remove('btn-paste-active');
  }
}

async function ctxPaste() {
  if (!clipboard) return;
  const targetDevice = currentDevice;
  const targetDir = (targetDevice === 'phone') ? currentPhonePath : (ctxIsDir ? ctxFile : currentPath);
  const src = clipboard;

  // 1. PC -> PC
  if (src.device === 'pc' && targetDevice === 'pc') {
    const dest = (targetDir.replace(/\/+$/, '')) + '/' + src.name;
    const ep = src.op === 'cut' ? '/api/move' : '/api/copy';
    try {
      const res = await fetch(`${getBase()}${ep}`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ src: src.path, dest })
      });
      const data = await res.json();
      if (data.error) {
        toast(`Paste failed: ${data.error}`, 'error');
      } else {
        toast(`Pasted "${src.name}" to PC!`, 'success');
        if (src.op === 'cut') clipboard = null;
        updateClipboardUI();
        loadFiles(currentPath);
      }
    } catch (err) {
      toast('Paste operation failed', 'error');
    }
  }
  // 2. Phone -> PC (Transfer file from mobile to PC!)
  else if (src.device === 'phone' && targetDevice === 'pc') {
    toast(`Transferring "${src.name}" from Phone to PC...`, 'info');
    try {
      const res = await fetch(`${getBase()}/api/phone/copy_to_pc`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Password': serverPass },
        body: JSON.stringify({ phone_path: src.path, pc_dest: targetDir, op: src.op })
      });
      const data = await res.json();
      if (data.success) {
        toast(`✓ Transferred "${src.name}" to PC!`, 'success');
        if (src.op === 'cut') clipboard = null;
        updateClipboardUI();
        loadFiles(currentPath);
      } else {
        toast('Transfer from Phone failed', 'error');
      }
    } catch (err) {
      toast('Transfer error', 'error');
    }
  }
  // 3. PC -> Phone (Transfer file from PC to mobile!)
  else if (src.device === 'pc' && targetDevice === 'phone') {
    toast(`Transferring "${src.name}" from PC to Phone...`, 'info');
    try {
      const res = await fetch(`${getBase()}/api/phone/copy_from_pc`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Password': serverPass },
        body: JSON.stringify({ pc_path: src.path, phone_dest: targetDir, op: src.op })
      });
      const data = await res.json();
      if (data.success) {
        toast(`✓ Transferred "${src.name}" to Phone!`, 'success');
        if (src.op === 'cut') clipboard = null;
        updateClipboardUI();
        loadPhoneDir(currentPhonePath);
      } else {
        toast('Transfer to Phone failed', 'error');
      }
    } catch (err) {
      toast('Transfer error', 'error');
    }
  }
  // 4. Phone -> Phone (Rename or move within phone)
  else if (src.device === 'phone' && targetDevice === 'phone') {
    const dest = (targetDir.replace(/\/+$/, '')) + '/' + src.name;
    try {
      const res = await fetch(`${getBase()}/api/phone/rename`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Password': serverPass },
        body: JSON.stringify({ src: src.path, dest })
      });
      const data = await res.json();
      if (data.success) {
        toast(`✓ Pasted "${src.name}" on Phone!`, 'success');
        if (src.op === 'cut') clipboard = null;
        updateClipboardUI();
        loadPhoneDir(currentPhonePath);
      } else {
        toast('Paste failed on Phone', 'error');
      }
    } catch (err) {
      toast('Paste error', 'error');
    }
  }
}

async function ctxDelete() {
  if (!confirm(`Are you sure you want to delete "${ctxName}"?`)) return;
  if (ctxDevice === 'phone') {
    try {
      const res = await fetch(`${getBase()}/api/phone/delete?path=${encodeURIComponent(ctxFile)}`, {
        headers: { 'X-Password': serverPass }
      });
      const data = await res.json();
      if (data.success) {
        toast(`Moved "${ctxName}" to Phone Recycle Bin (.WireFM_Trash)`, 'success');
        loadPhoneDir(currentPhonePath);
      } else {
        toast('Delete failed on Phone', 'error');
      }
    } catch (err) {
      toast('Delete request failed', 'error');
    }
  } else {
    try {
      const res = await fetch(`${getBase()}/api/delete?path=${encodeURIComponent(ctxFile)}`, {
        method: 'DELETE',
        headers: { 'X-Password': serverPass }
      });
      const data = await res.json();
      if (data.error) {
        toast(`Delete failed: ${data.error}`, 'error');
      } else {
        toast(`Moved "${ctxName}" to PC Recycle Bin`, 'success');
        loadFiles(currentPath);
      }
    } catch (err) {
      toast('Delete request failed', 'error');
    }
  }
}

async function ctxRename() {
  const newName = prompt('Enter new name:', ctxName);
  if (!newName || newName === ctxName) return;

  if (ctxDevice === 'phone') {
    const dir = ctxFile.substring(0, ctxFile.lastIndexOf('/')) || '/storage/emulated/0';
    const dest = (dir.replace(/\/+$/, '')) + '/' + newName;
    try {
      const res = await fetch(`${getBase()}/api/phone/rename`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Password': serverPass },
        body: JSON.stringify({ src: ctxFile, dest })
      });
      const data = await res.json();
      if (data.success) {
        toast('Renamed successfully on Phone', 'success');
        loadPhoneDir(currentPhonePath);
      } else {
        toast('Rename failed on Phone', 'error');
      }
    } catch (err) {
      toast('Rename request failed', 'error');
    }
  } else {
    const dir = ctxFile.substring(0, ctxFile.lastIndexOf('/')) || '/';
    const dest = (dir.replace(/\/+$/, '')) + '/' + newName;
    try {
      const res = await fetch(`${getBase()}/api/move`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ src: ctxFile, dest })
      });
      const data = await res.json();
      if (data.error) {
        toast(`Rename failed: ${data.error}`, 'error');
      } else {
        toast('Renamed successfully', 'success');
        loadFiles(currentPath);
      }
    } catch (err) {
      toast('Rename request failed', 'error');
    }
  }
}

function ctxRefresh() {
  if (currentDevice === 'phone') {
    loadPhoneDir(currentPhonePath);
  } else {
    loadFiles(currentPath);
  }
}

// BATCH UPLOAD
function showUpload() {
  document.getElementById('uploadInput').click();
}

async function handleUpload(input) {
  await uploadFilesList(input.files);
  input.value = '';
}

async function uploadFilesList(files) {
  if (!files || files.length === 0) return;
  toast(`Uploading ${files.length} file(s)...`);

  if (currentDevice === 'phone') {
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      const formData = new FormData();
      formData.append('files', f);
      try {
        const uploadRes = await fetch(`${getBase()}/api/upload?path=/tmp&password=${encodeURIComponent(serverPass)}`, {
          method: 'POST',
          body: formData
        });
        if (uploadRes.ok) {
          await fetch(`${getBase()}/api/phone/copy_from_pc`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Password': serverPass },
            body: JSON.stringify({ pc_path: '/tmp/' + f.name, phone_dest: currentPhonePath, op: 'cut' })
          });
        }
      } catch (e) {}
    }
    toast(`✓ Uploaded ${files.length} item(s) to Phone!`, 'success');
    loadPhoneDir(currentPhonePath);
    return;
  }

  const formData = new FormData();
  for (let i = 0; i < files.length; i++) {
    formData.append('files', files[i]);
  }

  try {
    const res = await fetch(`${getBase()}/api/upload?path=${encodeURIComponent(currentPath)}&password=${encodeURIComponent(serverPass)}`, {
      method: 'POST',
      body: formData
    });
    const data = await res.json();
    if (data.error) {
      toast(`Upload error: ${data.error}`, 'error');
    } else {
      toast(`Uploaded ${files.length} item(s) successfully!`, 'success');
      loadFiles(currentPath);
    }
  } catch (err) {
    toast('Upload failed. Check server connection.', 'error');
  }
}

// CREATE FOLDER
async function createFolder() {
  const name = prompt('Enter new folder name:');
  if (!name || !name.trim()) return;

  if (currentDevice === 'phone') {
    const path = (currentPhonePath.replace(/\/+$/, '')) + '/' + name.trim();
    try {
      const res = await fetch(`${getBase()}/api/phone/mkdir`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Password': serverPass },
        body: JSON.stringify({ path })
      });
      const data = await res.json();
      if (data.success) {
        toast(`Created folder "${name}" on Phone`, 'success');
        loadPhoneDir(currentPhonePath);
      } else {
        toast('Failed to create folder on Phone', 'error');
      }
    } catch (e) {
      toast('Create folder request failed', 'error');
    }
  } else {
    const path = (currentPath.replace(/\/+$/, '')) + '/' + name.trim();
    try {
      const res = await fetch(`${getBase()}/api/mkdir`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ path })
      });
      const data = await res.json();
      if (data.error) {
        toast(`Error creating folder: ${data.error}`, 'error');
      } else {
        toast(`Created folder "${name}"`, 'success');
        loadFiles(currentPath);
      }
    } catch (err) {
      toast('Create folder request failed', 'error');
    }
  }
}

// DRAG AND DROP HANDLERS
window.addEventListener('dragenter', (e) => {
  e.preventDefault();
  document.getElementById('dropOverlay').classList.add('active');
  const folderName = currentPath.split('/').pop() || 'Root';
  document.getElementById('dropFolderTarget').textContent = `Target: ${folderName}`;
});

window.addEventListener('dragover', (e) => {
  e.preventDefault();
});

window.addEventListener('dragleave', (e) => {
  if (e.relatedTarget === null) {
    document.getElementById('dropOverlay').classList.remove('active');
  }
});

window.addEventListener('drop', async (e) => {
  e.preventDefault();
  document.getElementById('dropOverlay').classList.remove('active');
  if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
    await uploadFilesList(e.dataTransfer.files);
  }
});

// ESC KEY HANDLER
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    closePreview();
    if (isConnected) {
      document.getElementById('connectModal').classList.add('hidden');
    }
  }
});

// TOAST NOTIFICATIONS
function toast(msg, type = '') {
  const container = document.getElementById('toasts');
  const t = document.createElement('div');
  t.className = 'toast';
  if (type === 'error') {
    t.style.borderColor = 'var(--danger)';
    t.style.color = '#fca5a5';
  } else if (type === 'success') {
    t.style.borderColor = 'var(--success)';
    t.style.color = '#86efac';
  }
  t.textContent = msg;
  container.appendChild(t);
  setTimeout(() => {
    t.style.opacity = '0';
    t.style.transform = 'translateY(10px)';
    setTimeout(() => t.remove(), 250);
  }, 3200);
}

function esc(s) {
  return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escJ(s) {
  return String(s || '').replace(/'/g, "\\'").replace(/\\/g, '\\\\');
}
