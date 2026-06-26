docker-compose up -d	Starts all containers in the background	First start or restart existing containers

docker-compose up --build -d	Rebuilds images and starts containers	After changing backend code, Dockerfile, or anything inside the image


docker-compose down	Stops and removes containers and networks	When you want to completely stop the application


docker-compose down -v	Stops containers and deletes volumes too	When you want a fresh database and clean environment


docker-compose restart	Restarts running containers	After small configuration changes or if a service is stuck


docker-compose ps	Shows container status	To check what's running and what's stopped
docker-compose logs	Shows container logs	To debug errors


docker-compose logs -f	Shows live logs continuously	To watch application startup or debug issues in real time


docker-compose down && docker-compose up -d	Full restart of the application	When containers are behaving strangely