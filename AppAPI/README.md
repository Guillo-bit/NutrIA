# Nutrition Analysis API

A FastAPI-based REST API for analyzing food images and providing nutritional information. This API integrates with Google Gemini AI for food detection and USDA FoodData Central for nutritional data.

## Features

- 🖼️ **Image Analysis**: Upload food images and get automatic food detection
- 🤖 **AI-Powered**: Uses Google Gemini AI to identify foods in images
- 📊 **Nutritional Data**: Integrates with USDA FoodData Central for comprehensive nutritional information
- 🍎 **Multiple Foods**: Can detect and analyze multiple foods in a single image
- 📱 **Flutter Ready**: CORS enabled for mobile app integration
- 📚 **Auto Documentation**: Swagger/OpenAPI documentation available

## API Endpoints

### POST /analyze-food

Analyze a food image and return nutritional information.

**Request:**

- Method: `POST`
- Content-Type: `multipart/form-data`
- Body: Image file (JPEG/PNG)

**Response:**

```json
{
  "success": true,
  "foods_detected": [
    {
      "name": "apple",
      "nutrition": {
        "calories": 95,
        "protein": 0.5,
        "carbs": 25,
        "fat": 0.3,
        "fiber": 4.4,
        "sugar": 19.0,
        "sodium": 2.0,
        "cholesterol": 0.0
      }
    }
  ],
  "total_nutrition": {
    "calories": 95,
    "protein": 0.5,
    "carbs": 25,
    "fat": 0.3,
    "fiber": 4.4,
    "sugar": 19.0,
    "sodium": 2.0,
    "cholesterol": 0.0
  }
}
```

### GET /health

Health check endpoint.

**Response:**

```json
{
  "status": "healthy",
  "service": "nutrition-analysis-api"
}
```

## Setup Instructions

### 1. Prerequisites

- Python 3.8+
- pip package manager

### 2. Clone and Setup

```bash
# Clone the repository
git clone <repository-url>
cd nutrition-analysis-api

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

### 3. API Keys Configuration

#### Get Gemini API Key

1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Sign in with your Google account
3. Create a new API key
4. Copy the key

#### Get USDA API Key

1. Visit [USDA API Key Signup](https://fdc.nal.usda.gov/api-key-signup.html)
2. Fill out the form
3. Check your email for the API key
4. Copy the key

#### Configure Environment Variables

```bash
# Copy the example environment file
cp .env.example .env

# Edit .env file and add your API keys
GEMINI_API_KEY=your_gemini_api_key_here
USDA_API_KEY=your_usda_api_key_here
```

### 4. Run the Application

```bash
# Start the server
python main.py

# Or using uvicorn directly
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 5. Access Documentation

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## Testing

### Using the Test Script

```bash
# Run the test script
python test_api.py
```

### Using curl

```bash
# Test with a food image
curl -X POST "http://localhost:8000/analyze-food" \
  -F "file=@path/to/your/food-image.jpg" \
  -H "Content-Type: multipart/form-data"
```

### Using Python

```python
import requests

url = "http://localhost:8000/analyze-food"
files = {'file': open('food-image.jpg', 'rb')}

response = requests.post(url, files=files)
data = response.json()

print(f"Detected foods: {[food['name'] for food in data['foods_detected']]}")
print(f"Total calories: {data['total_nutrition']['calories']}")
```

## Flutter Integration Example

```dart
import 'dart:io';
import 'package:http/http.dart' as http;

Future<void> analyzeFoodImage(File imageFile) async {
  var request = http.MultipartRequest(
    'POST',
    Uri.parse('http://your-server-ip:8000/analyze-food'),
  );

  request.files.add(
    await http.MultipartFile.fromPath('file', imageFile.path),
  );

  var response = await request.send();

  if (response.statusCode == 200) {
    var responseData = await response.stream.bytesToString();
    var data = jsonDecode(responseData);

    print('Foods detected: ${data['foods_detected']}');
    print('Total nutrition: ${data['total_nutrition']}');
  } else {
    print('Error: ${response.statusCode}');
  }
}
```

## Error Handling

The API includes comprehensive error handling:

- **400 Bad Request**: Invalid image file or missing parameters
- **422 Unprocessable Entity**: Validation errors
- **500 Internal Server Error**: Server-side errors

All errors return a JSON response with error details:

```json
{
  "detail": "Error description"
}
```

## Rate Limits

- USDA API: 1,000 requests per hour per IP address
- Gemini API: Varies by plan (check your Google AI Studio dashboard)

## Production Deployment

### Using Docker (Recommended)

```dockerfile
FROM python:3.9-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### Environment Variables for Production

```bash
# Update CORS origins for your Flutter app
CORS_ORIGINS=https://your-flutter-app.com

# Set production logging level
LOG_LEVEL=WARNING

# API Keys (use secure secret management)
GEMINI_API_KEY=your_production_gemini_key
USDA_API_KEY=your_production_usda_key
```

## Troubleshooting

### Common Issues

1. **"GEMINI_API_KEY not found"**

   - Make sure your `.env` file exists and contains the Gemini API key
   - Check that the key is correctly formatted

2. **"USDA_API_KEY not found"**

   - Verify your `.env` file contains the USDA API key
   - Ensure the key is active and not expired

3. **CORS errors from Flutter app**

   - Update the CORS origins in the FastAPI configuration
   - Make sure your Flutter app is making requests to the correct server URL

4. **Food detection not working**

   - Check that your Gemini API key has sufficient quota
   - Verify the image quality and lighting in your photos
   - Check server logs for detailed error messages

5. **Nutritional data not found**
   - Some foods might not be in the USDA database
   - Try using more specific food names (e.g., "granny smith apple" instead of "apple")
   - Check the server logs for USDA API response details

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is open source and available under the MIT License.

## Support

For issues and questions:

1. Check the troubleshooting section
2. Review the API documentation
3. Check server logs for error details
4. Open an issue in the repository
