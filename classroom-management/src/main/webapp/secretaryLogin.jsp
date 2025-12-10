<!DOCTYPE html>
<html>
<head>
    <title>Secretary Login</title>
    <style>
        body { 
            font-family: Arial; 
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .login-card {
            background: white;
            padding: 40px;
            border-radius: 10px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.2);
            width: 400px;
        }
        h2 { text-align: center; color: #333; margin-bottom: 30px; }
        .form-group { margin-bottom: 20px; }
        label { display: block; margin-bottom: 5px; color: #555; font-weight: bold; }
        input { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 5px; font-size: 14px; }
        .btn { width: 100%; padding: 12px; background: #2196F3; color: white; border: none; border-radius: 5px; font-size: 16px; cursor: pointer; }
        .btn:hover { background: #1976D2; }
        .error { background: #f8d7da; color: #721c24; padding: 10px; border-radius: 5px; margin-bottom: 20px; }
        .info { background: #d1ecf1; color: #0c5460; padding: 10px; border-radius: 5px; margin-bottom: 20px; font-size: 13px; }
        .back-link { text-align: center; margin-top: 20px; }
        .back-link a { color: #2196F3; text-decoration: none; }
    </style>
</head>
<body>
    <div class="login-card">
        <h2>📋 Secretary Login</h2>
        
        <% if (request.getAttribute("error") != null) { %>
            <div class="error"><%= request.getAttribute("error") %></div>
        <% } %>
        
        <div class="info">
            <strong>Demo Login:</strong><br>
            Username: alice@univ.edu<br>
            (Any username from Instructor table works)
        </div>
        
        <form action="secretary/login" method="POST">
            <div class="form-group">
                <label>Email</label>
                <input type="email" name="username" required placeholder="your.email@univ.edu">
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" name="password" required placeholder="Enter password">
            </div>
            <button type="submit" class="btn">Login</button>
        </form>
        
        <div class="back-link">
            <a href="/classroom-management/">← Back to Home</a>
        </div>
    </div>
</body>
</html>
