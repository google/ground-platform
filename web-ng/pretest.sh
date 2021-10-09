echo "Running 'pretest.sh' script to download Google Maps API for testing..."
rm google-maps-api.js
date +"// Download time: %Y-%m-%d %H:%M" >> google-maps-api.js
curl 'https://maps.googleapis.com/maps/api/js?sensor=false&libraries=geometry' >> google-maps-api.js
echo "'pretest.sh' finished"