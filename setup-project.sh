#!/bin/bash

echo "🚀 Setting up Classroom Management System..."

# Navigate to project directory
cd ~/Downloads/440_project

# Remove old project if exists
if [ -d "classroom-management" ]; then
    echo "📦 Removing old project..."
    rm -rf classroom-management
fi

# Create new project
echo "📁 Creating project structure..."
mkdir -p classroom-management
cd classroom-management

# Create directory structure
mkdir -p src/main/java/com/classroom/{servlet,service,dao,model}
mkdir -p src/main/webapp/WEB-INF
mkdir -p src/main/resources
mkdir -p src/test/java

echo "✅ Project structure created!"
echo ""
echo "📝 Next steps:"
echo "1. Copy the pom.xml to the root directory"
echo "2. Copy DatabaseConnection.java to src/main/java/com/classroom/dao/"
echo "3. Copy SchedulerService.java to src/main/java/com/classroom/service/"
echo "4. Copy AdminServlet.java to src/main/java/com/classroom/servlet/"
echo "5. Copy TestServlet.java to src/main/java/com/classroom/servlet/"
echo "6. Create index.jsp in src/main/webapp/"
echo ""
echo "Then run: mvn clean compile"
echo ""
echo "📂 Your project structure:"
tree -L 5 src/ 2>/dev/null || find src -type d

echo ""
echo "✅ Setup complete! Ready to add your Java files."