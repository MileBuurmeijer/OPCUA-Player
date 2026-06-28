let socket;
let reconnectDelay = 1000;
const maxReconnectDelay = 30000;

// Current form mode: 'player' or 'recorder'
let currentMode = 'player';

// Page load
document.addEventListener('DOMContentLoaded', () => {
    connectWS();
    loadWorkspaceFiles();
});

// Fetch configuration and data files from REST API
function loadWorkspaceFiles() {
    fetch('/api/files')
        .then(res => res.json())
        .then(files => {
            const configList = document.getElementById('configfiles-list');
            const dataList = document.getElementById('datafiles-list');
            
            configList.innerHTML = '';
            dataList.innerHTML = '';
            
            files.forEach(file => {
                const opt1 = document.createElement('option');
                opt1.value = file;
                configList.appendChild(opt1);
                
                const opt2 = document.createElement('option');
                opt2.value = file;
                dataList.appendChild(opt2);
            });
        })
        .catch(err => {
            console.error("Failed to load workspace files", err);
        });
}

// WebSocket Connection Setup
function connectWS() {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/ws`;
    
    socket = new WebSocket(wsUrl);
    
    socket.onopen = () => {
        const badge = document.getElementById('ws-status');
        badge.className = "connection-badge status-connected";
        badge.querySelector('.status-text').textContent = "Connected";
        reconnectDelay = 1000;
    };
    
    socket.onclose = () => {
        const badge = document.getElementById('ws-status');
        badge.className = "connection-badge status-disconnected";
        badge.querySelector('.status-text').textContent = "Disconnected (Retrying...)";
        
        setTimeout(connectWS, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, maxReconnectDelay);
    };

    socket.onerror = (err) => {
        console.error("WebSocket Error:", err);
    };
    
    socket.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            handleWSMessage(msg);
        } catch (e) {
            console.error("Error parsing WebSocket message", e);
        }
    };
}

// Toggle between Player and Recorder forms
function switchMode(mode) {
    currentMode = mode;
    
    const playerBtn = document.getElementById('toggle-player');
    const recorderBtn = document.getElementById('toggle-recorder');
    const playerFields = document.getElementById('player-fields');
    const recorderFields = document.getElementById('recorder-fields');
    
    if (mode === 'player') {
        playerBtn.classList.add('active');
        recorderBtn.classList.remove('active');
        playerFields.classList.remove('hidden');
        recorderFields.classList.add('hidden');
        document.getElementById('inst-uri').value = 'OPCUA-Player';
    } else {
        playerBtn.classList.remove('active');
        recorderBtn.classList.add('active');
        playerFields.classList.add('hidden');
        recorderFields.classList.remove('hidden');
        document.getElementById('inst-uri').value = 'opc.tcp://localhost:12400/OPCUA-Player';
    }
}

function toggleCaptureFields(checkbox) {
    const group = document.getElementById('capture-node-group');
    if (checkbox.checked) {
        group.classList.remove('hidden');
    } else {
        group.classList.add('hidden');
    }
}

// Handle Form Submit (Create Instance)
function onCreateInstance(e) {
    e.preventDefault();
    
    const params = {
        type: currentMode,
        configfile: document.getElementById('inst-configfile').value,
        datafile: document.getElementById('inst-datafile').value,
        uri: document.getElementById('inst-uri').value
    };
    
    if (currentMode === 'player') {
        params.port = parseFloat(document.getElementById('inst-port').value);
        params.namespace = document.getElementById('inst-namespace').value;
        params.servicename = document.getElementById('inst-servicename').value;
    } else {
        params.duration = document.getElementById('inst-duration').value;
        params.publishingInterval = parseFloat(document.getElementById('inst-pub-interval').value);
        params.samplingInterval = parseFloat(document.getElementById('inst-samp-interval').value);
        params.captureModel = document.getElementById('inst-capture-model').checked;
        if (params.captureModel) {
            params.startNode = document.getElementById('inst-start-node').value;
        }
    }
    
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({
            action: 'create',
            params: params
        }));
    }
}

// WebSocket Message Router
function handleWSMessage(msg) {
    switch (msg.type) {
        case 'init':
            renderAllInstances(msg.instances);
            break;
        case 'created':
            addInstanceCard(msg.instance);
            break;
        case 'status':
            updateInstanceStatus(msg.id, msg.status);
            break;
        case 'log':
            addLogLine(msg.id, msg.line);
            break;
        case 'node_tree':
            renderNodeTree(msg.id, msg.tree);
            break;
        case 'node_value':
            updateNodeValue(msg.id, msg.nodeId, msg.value);
            break;
        case 'removed':
            removeInstanceCard(msg.id);
            break;
        case 'error':
            alert("Error: " + msg.message);
            break;
    }
}

// Action triggers
function triggerStart(id) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ action: 'start', id: id }));
    }
}

function triggerStop(id) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ action: 'stop', id: id }));
    }
}

function triggerControl(id, cmdCode) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ action: 'control', id: id, command: cmdCode }));
    }
}

function triggerRemove(id) {
    if (confirm(`Are you sure you want to remove instance ${id}? This will stop the process if it is running.`)) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ action: 'remove', id: id }));
        }
    }
}

function removeInstanceCard(id) {
    const card = document.getElementById(`card-${id}`);
    if (card) {
        card.remove();
    }
    const container = document.getElementById('instances-container');
    if (container.children.length === 0) {
        document.getElementById('no-instances').classList.remove('hidden');
    }
}

// Render UI Components
function renderAllInstances(instancesList) {
    const container = document.getElementById('instances-container');
    const emptyState = document.getElementById('no-instances');
    
    container.innerHTML = '';
    
    if (instancesList.length === 0) {
        emptyState.classList.remove('hidden');
    } else {
        emptyState.classList.add('hidden');
        instancesList.forEach(inst => {
            addInstanceCard(inst);
            // If logs/node tree exist, fill them
            inst.logs.forEach(line => addLogLine(inst.id, line));
            if (inst.nodeTree && inst.nodeTree.length > 0) {
                renderNodeTree(inst.id, inst.nodeTree);
                Object.keys(inst.nodeValues).forEach(nodeId => {
                    updateNodeValue(inst.id, nodeId, inst.nodeValues[nodeId]);
                });
            }
        });
    }
}

function addInstanceCard(inst) {
    const container = document.getElementById('instances-container');
    document.getElementById('no-instances').classList.add('hidden');
    
    // Check if card already exists
    if (document.getElementById(`card-${inst.id}`)) {
        return;
    }
    
    const card = document.createElement('div');
    card.id = `card-${inst.id}`;
    card.className = `instance-card status-${inst.status}`;
    
    const typeLabel = inst.type.toUpperCase();
    const typeIcon = inst.type === 'player' ? 'fa-play-circle' : 'fa-circle-dot';
    
    let metaHTML = '';
    let lifecycleButtonsHTML = '';
    let controlButtonsHTML = '';
    
    if (inst.type === 'player') {
        metaHTML = `
            <div>Endpoint: <span>opc.tcp://localhost:${inst.port}/${inst.uri}</span></div>
            <div>Namespace: <span>${inst.namespace}</span></div>
            <div>Config: <span>${inst.configfile}</span></div>
            <div>Data: <span>${inst.datafile || 'Live Stream'}</span></div>
        `;
        lifecycleButtonsHTML = `
            <button id="btn-start-${inst.id}" class="action-btn btn-start" onclick="triggerStart('${inst.id}')"><i class="fa-solid fa-play"></i> Start</button>
            <button id="btn-remove-${inst.id}" class="action-btn btn-remove" onclick="triggerRemove('${inst.id}')"><i class="fa-solid fa-trash"></i> Remove</button>
        `;
        controlButtonsHTML = `
            <button id="btn-play-${inst.id}" class="action-btn btn-start" onclick="triggerControl('${inst.id}', 1)" disabled><i class="fa-solid fa-circle-play"></i> Play</button>
            <button id="btn-pause-${inst.id}" class="action-btn btn-pause" onclick="triggerControl('${inst.id}', 5)" disabled><i class="fa-solid fa-pause"></i> Pause/Resume</button>
            <button id="btn-stop-${inst.id}" class="action-btn btn-stop" onclick="triggerStop('${inst.id}')" disabled><i class="fa-solid fa-stop"></i> Stop</button>
        `;
    } else {
        metaHTML = `
            <div>Server Target: <span>${inst.uri}</span></div>
            <div>Duration: <span>${inst.duration || 'Unlimited'}</span></div>
            <div>Config (Tags): <span>${inst.configfile}</span></div>
            <div>Data Output: <span>${inst.datafile}</span></div>
            <div>Capture Mode: <span>${inst.captureModel ? 'Yes (' + inst.startNode + ')' : 'No'}</span></div>
        `;
        lifecycleButtonsHTML = `
            <button id="btn-populate-${inst.id}" class="action-btn btn-populate" onclick="triggerPopulateNamespace('${inst.id}')"><i class="fa-solid fa-play"></i> Start</button>
            <button id="btn-remove-${inst.id}" class="action-btn btn-remove" onclick="triggerRemove('${inst.id}')"><i class="fa-solid fa-trash"></i> Remove</button>
        `;
        controlButtonsHTML = `
            <button id="btn-start-${inst.id}" class="action-btn btn-start" onclick="triggerStart('${inst.id}')"><i class="fa-solid fa-circle-dot"></i> Record</button>
            <button id="btn-pause-${inst.id}" class="action-btn btn-pause" onclick="triggerControl('${inst.id}', 5)" disabled><i class="fa-solid fa-pause"></i> Pause/Resume</button>
            <button id="btn-stop-${inst.id}" class="action-btn btn-stop" onclick="triggerStop('${inst.id}')" disabled><i class="fa-solid fa-stop"></i> Stop</button>
        `;
    }
    
    card.innerHTML = `
        <div class="instance-header">
            <div class="instance-title">
                <i class="fa-solid ${typeIcon} instance-type-icon"></i>
                <h3>Instance: ${inst.id}</h3>
                <span>${typeLabel}</span>
            </div>
            <div class="header-actions" style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
                ${lifecycleButtonsHTML}
            </div>
        </div>
        
        <div class="instance-meta">
            ${metaHTML}
        </div>
        
        <div class="instance-controls">
            ${controlButtonsHTML}
            <span class="status-indicator" style="margin-left: auto;">${inst.status}</span>
        </div>
        
        <div class="instance-details">
            <!-- OPC UA Node explorer / Recorder stats -->
            <div class="tree-view">
                <div class="panel-header">
                    <span><i class="fa-solid fa-network-wired"></i> OPC-UA Namespace (Client View)</span>
                </div>
                <div id="tree-body-${inst.id}" class="tree-body">
                    <div class="no-nodes">Connect and start to explore nodes.</div>
                </div>
            </div>
            
            <!-- Terminal console -->
            <div class="terminal-view">
                <div class="panel-header">
                    <span><i class="fa-solid fa-terminal"></i> Console Output</span>
                    <button class="action-btn" style="padding: 0.1rem 0.4rem; font-size: 0.75rem;" onclick="clearLogs('${inst.id}')">Clear</button>
                </div>
                <div id="console-body-${inst.id}" class="console-body">
                    <!-- Logs stream -->
                </div>
            </div>
        </div>
    `;
    
    container.appendChild(card);
    updateButtonStates(inst.id, inst.status);
}

function updateInstanceStatus(id, status) {
    const card = document.getElementById(`card-${id}`);
    if (!card) return;
    
    // Replace old status class
    card.className = card.className.replace(/status-\w+/g, `status-${status}`);
    card.querySelector('.status-indicator').textContent = status;
    
    updateButtonStates(id, status);
}

function updateButtonStates(id, status) {
    const btnStart = document.getElementById(`btn-start-${id}`);
    const btnPlay = document.getElementById(`btn-play-${id}`);
    const btnPause = document.getElementById(`btn-pause-${id}`);
    const btnStop = document.getElementById(`btn-stop-${id}`);
    const btnPopulate = document.getElementById(`btn-populate-${id}`);
    
    if (status === 'Stopped' || status === 'Completed' || status === 'Failed') {
        if (btnStart) btnStart.disabled = false;
        if (btnPlay) btnPlay.disabled = true;
        if (btnPause) btnPause.disabled = true;
        if (btnStop) btnStop.disabled = true;
        if (btnPopulate) btnPopulate.disabled = false;
    } else if (status === 'Starting') {
        if (btnStart) btnStart.disabled = true;
        if (btnPlay) btnPlay.disabled = true;
        if (btnPause) btnPause.disabled = true;
        if (btnStop) btnStop.disabled = false;
        if (btnPopulate) btnPopulate.disabled = true;
    } else if (status === 'Running' || status === 'Initialized') {
        if (btnStart) btnStart.disabled = true;
        if (btnPlay) btnPlay.disabled = false;
        if (btnPause) btnPause.disabled = true;
        if (btnStop) btnStop.disabled = false;
        if (btnPopulate) btnPopulate.disabled = true;
    } else if (status === 'PlayForward' || status === 'PlayFastForward' || status === 'PlayBackward' || status === 'PlayFastBackward') {
        if (btnStart) btnStart.disabled = true;
        if (btnPlay) btnPlay.disabled = false;
        if (btnPause) btnPause.disabled = false;
        if (btnStop) btnStop.disabled = false;
        if (btnPopulate) btnPopulate.disabled = true;
    } else if (status === 'Paused') {
        if (btnStart) btnStart.disabled = true;
        if (btnPlay) btnPlay.disabled = true;
        if (btnPause) btnPause.disabled = false;
        if (btnStop) btnStop.disabled = false;
        if (btnPopulate) btnPopulate.disabled = true;
    } else if (status === 'Recording') {
        if (btnStart) btnStart.disabled = true;
        if (btnStop) btnStop.disabled = false;
        if (btnPopulate) btnPopulate.disabled = true;
        if (btnPause) btnPause.disabled = false;
    }
}

function addLogLine(id, line) {
    const consoleBody = document.getElementById(`console-body-${id}`);
    if (!consoleBody) return;
    
    const div = document.createElement('div');
    if (line.includes('[SYSTEM]')) {
        div.className = 'system-line';
        div.textContent = line.replace('[SYSTEM]', '[System]');
    } else {
        const lowerLine = line.toLowerCase();
        if (lowerLine.includes('severe:') || 
            lowerLine.includes('exception') || 
            lowerLine.includes('error') || 
            lowerLine.includes('failed')) {
            div.className = 'error-line';
        } else if (lowerLine.includes('warning:') || 
                   lowerLine.includes('warn') || 
                   lowerLine.includes('warning')) {
            div.className = 'warning-line';
        } else {
            div.className = 'player-line';
        }
        div.textContent = line;
    }
    consoleBody.appendChild(div);
    
    // Auto scroll to bottom
    consoleBody.scrollTop = consoleBody.scrollHeight;
}

function clearLogs(id) {
    const consoleBody = document.getElementById(`console-body-${id}`);
    if (consoleBody) {
        consoleBody.innerHTML = '';
    }
}

function renderNodeTree(instId, nodes) {
    const container = document.getElementById(`tree-body-${instId}`);
    if (!container) return;
    if (!nodes || nodes.length === 0) {
        container.innerHTML = '<div class="no-nodes">No nodes exposed or client disconnected.</div>';
        return;
    }
    container.innerHTML = '';
    
    const nodeMap = {};
    nodes.forEach(n => {
        nodeMap[n.id] = { ...n, children: [] };
    });
    
    const roots = [];
    nodes.forEach(n => {
        const mappedNode = nodeMap[n.id];
        if (!n.parentId || !nodeMap[n.parentId]) {
            roots.push(mappedNode);
        } else {
            nodeMap[n.parentId].children.push(mappedNode);
        }
    });
    
    function buildHTML(node, depth) {
        const isFolder = node.type === 'Object';
        const iconClass = isFolder ? 'fa-folder icon-folder' : 'fa-circle-dot icon-tag';
        const nodeHtml = document.createElement('div');
        nodeHtml.className = 'tree-node';
        
        const infoHtml = document.createElement('div');
        infoHtml.className = 'tree-node-info';
        
        infoHtml.innerHTML = `
            <i class="fa-solid ${iconClass} tree-node-icon"></i>
            <span class="tree-node-name">${node.name}</span>
            <span class="tree-node-id">${node.id}</span>
        `;
        
        if (!isFolder) {
            const valSpan = document.createElement('span');
            valSpan.id = `val-${instId}-${node.id.replace(/=/g, '_').replace(/;/g, '_')}`;
            valSpan.className = 'tree-node-val';
            valSpan.innerText = '-';
            infoHtml.appendChild(valSpan);
        }
        
        nodeHtml.appendChild(infoHtml);
        
        if (node.children && node.children.length > 0) {
            node.children.forEach(child => {
                nodeHtml.appendChild(buildHTML(child, depth + 1));
            });
        }
        
        return nodeHtml;
    }
    
    roots.forEach(root => {
        container.appendChild(buildHTML(root, 0));
    });
}

function updateNodeValue(instId, nodeId, value) {
    const elementId = `val-${instId}-${nodeId.replace(/=/g, '_').replace(/;/g, '_')}`;
    const valSpan = document.getElementById(elementId);
    if (valSpan) {
        valSpan.innerText = value;
        valSpan.classList.add('val-flash');
        setTimeout(() => {
            valSpan.classList.remove('val-flash');
        }, 500);
    }
}

function triggerPopulateNamespace(id) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({
            action: 'populate_namespace',
            id: id
        }));
    }
}
