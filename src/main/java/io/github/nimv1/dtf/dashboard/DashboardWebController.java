package io.github.nimv1.dtf.dashboard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controller for serving the Task Dashboard web UI.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
@Controller
@ConditionalOnProperty(prefix = "dtf.dashboard", name = "enabled", havingValue = "true")
public class DashboardWebController {

    private static final String DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DTF Dashboard</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://unpkg.com/lucide@latest"></script>
    <style>
        .gradient-bg { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
        .card { background: rgba(255,255,255,0.95); backdrop-filter: blur(10px); }
        .stat-card { transition: transform 0.2s; }
        .stat-card:hover { transform: translateY(-2px); }
    </style>
</head>
<body class="bg-gray-100 min-h-screen">
    <div class="gradient-bg text-white py-6 px-8 shadow-lg">
        <div class="max-w-7xl mx-auto flex items-center justify-between">
            <div class="flex items-center gap-3">
                <i data-lucide="layers" class="w-8 h-8"></i>
                <h1 class="text-2xl font-bold">Distributed Task Framework</h1>
            </div>
            <div class="flex items-center gap-2 text-sm opacity-90">
                <i data-lucide="activity" class="w-4 h-4"></i>
                <span id="status">Connecting...</span>
            </div>
        </div>
    </div>

    <div class="max-w-7xl mx-auto px-8 py-8">
        <!-- Stats Grid -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div class="card stat-card rounded-xl p-6 shadow-md">
                <div class="flex items-center justify-between">
                    <div>
                        <p class="text-gray-500 text-sm">Queue Size</p>
                        <p id="queueSize" class="text-3xl font-bold text-gray-800">-</p>
                    </div>
                    <div class="bg-blue-100 p-3 rounded-full">
                        <i data-lucide="inbox" class="w-6 h-6 text-blue-600"></i>
                    </div>
                </div>
            </div>
            <div class="card stat-card rounded-xl p-6 shadow-md">
                <div class="flex items-center justify-between">
                    <div>
                        <p class="text-gray-500 text-sm">Processing</p>
                        <p id="processing" class="text-3xl font-bold text-gray-800">-</p>
                    </div>
                    <div class="bg-yellow-100 p-3 rounded-full">
                        <i data-lucide="loader" class="w-6 h-6 text-yellow-600"></i>
                    </div>
                </div>
            </div>
            <div class="card stat-card rounded-xl p-6 shadow-md">
                <div class="flex items-center justify-between">
                    <div>
                        <p class="text-gray-500 text-sm">Completed</p>
                        <p id="completed" class="text-3xl font-bold text-gray-800">-</p>
                    </div>
                    <div class="bg-green-100 p-3 rounded-full">
                        <i data-lucide="check-circle" class="w-6 h-6 text-green-600"></i>
                    </div>
                </div>
            </div>
            <div class="card stat-card rounded-xl p-6 shadow-md">
                <div class="flex items-center justify-between">
                    <div>
                        <p class="text-gray-500 text-sm">Failed</p>
                        <p id="failed" class="text-3xl font-bold text-gray-800">-</p>
                    </div>
                    <div class="bg-red-100 p-3 rounded-full">
                        <i data-lucide="x-circle" class="w-6 h-6 text-red-600"></i>
                    </div>
                </div>
            </div>
        </div>

        <!-- Task Lookup -->
        <div class="card rounded-xl p-6 shadow-md mb-8">
            <h2 class="text-lg font-semibold text-gray-800 mb-4 flex items-center gap-2">
                <i data-lucide="search" class="w-5 h-5"></i>
                Task Lookup
            </h2>
            <div class="flex gap-4">
                <input type="text" id="taskId" placeholder="Enter Task ID..."
                    class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none">
                <button onclick="lookupTask()" 
                    class="px-6 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition flex items-center gap-2">
                    <i data-lucide="search" class="w-4 h-4"></i>
                    Search
                </button>
            </div>
            <div id="taskResult" class="mt-4 hidden">
                <pre class="bg-gray-800 text-green-400 p-4 rounded-lg overflow-x-auto text-sm"></pre>
            </div>
        </div>

        <!-- Info -->
        <div class="card rounded-xl p-6 shadow-md">
            <h2 class="text-lg font-semibold text-gray-800 mb-4 flex items-center gap-2">
                <i data-lucide="info" class="w-5 h-5"></i>
                About
            </h2>
            <p class="text-gray-600">
                Distributed Task Framework Dashboard provides real-time monitoring of your task queue.
                View statistics, search for tasks, and manage your distributed workloads.
            </p>
            <div class="mt-4 flex gap-4">
                <a href="https://github.com/NiMv1/distributed-task-framework" target="_blank"
                    class="text-purple-600 hover:text-purple-800 flex items-center gap-1">
                    <i data-lucide="github" class="w-4 h-4"></i>
                    GitHub
                </a>
                <a href="/dtf/api/stats" target="_blank"
                    class="text-purple-600 hover:text-purple-800 flex items-center gap-1">
                    <i data-lucide="code" class="w-4 h-4"></i>
                    API
                </a>
            </div>
        </div>
    </div>

    <script>
        lucide.createIcons();

        async function fetchStats() {
            try {
                const res = await fetch('/dtf/api/stats');
                const data = await res.json();
                document.getElementById('queueSize').textContent = data.queueSize || 0;
                document.getElementById('status').textContent = 'Connected';
                document.getElementById('status').parentElement.classList.add('text-green-200');
            } catch (e) {
                document.getElementById('status').textContent = 'Disconnected';
                document.getElementById('status').parentElement.classList.add('text-red-200');
            }
        }

        async function lookupTask() {
            const taskId = document.getElementById('taskId').value.trim();
            if (!taskId) return;
            
            try {
                const res = await fetch(`/dtf/api/tasks/${taskId}`);
                const resultDiv = document.getElementById('taskResult');
                const pre = resultDiv.querySelector('pre');
                
                if (res.ok) {
                    const data = await res.json();
                    pre.textContent = JSON.stringify(data, null, 2);
                } else {
                    pre.textContent = 'Task not found';
                }
                resultDiv.classList.remove('hidden');
            } catch (e) {
                console.error(e);
            }
        }

        // Initial fetch and refresh every 5 seconds
        fetchStats();
        setInterval(fetchStats, 5000);
        
        // Enter key support
        document.getElementById('taskId').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') lookupTask();
        });
    </script>
</body>
</html>
""";

    /**
     * Serves the dashboard HTML page.
     */
    @GetMapping(value = "/dtf/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String dashboard() {
        return DASHBOARD_HTML;
    }
}
