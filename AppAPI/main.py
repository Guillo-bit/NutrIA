import os
import json
import requests
import logging
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import google.generativeai as genai
from dotenv import load_dotenv
import aiohttp
import asyncio

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize FastAPI app
app = FastAPI(
    title="Nutrition Analysis API",
    description="API for analyzing food images and providing nutritional information",
    version="1.0.0",
)

# Configure CORS for Flutter app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify your Flutter app domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Configure Gemini API
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))
gemini_model = genai.GenerativeModel("gemini-2.0-flash-exp")

# USDA API configuration
USDA_API_KEY = os.getenv("USDA_API_KEY")
USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1"


class NutritionInfo(BaseModel):
    calories: float = 0.0
    protein: float = 0.0
    carbs: float = 0.0
    fat: float = 0.0
    fiber: float = 0.0
    sugar: float = 0.0
    sodium: float = 0.0
    cholesterol: float = 0.0


class FoodItem(BaseModel):
    name: str
    nutrition: NutritionInfo


class NutritionResponse(BaseModel):
    success: bool
    foods_detected: List[FoodItem]
    total_nutrition: NutritionInfo


async def detect_foods_with_gemini(image_bytes: bytes) -> List[str]:
    """Use Gemini API to detect foods in the image"""
    try:
        # Upload image to Gemini
        image_part = {"mime_type": "image/jpeg", "data": image_bytes}

        prompt = 'Identify food and ingredients present in the image and give the names of each back in a JSON format like: {"foods": ["apple", "banana", "rice"]}. Only return the JSON, no additional text.'

        response = await asyncio.to_thread(
            gemini_model.generate_content, [prompt, image_part]
        )

        # Parse the response
        response_text = response.text.strip()
        logger.info(f"Gemini response: {response_text}")

        # Try to extract JSON from response
        try:
            # Look for JSON pattern in response
            json_start = response_text.find("{")
            json_end = response_text.rfind("}") + 1
            if json_start != -1 and json_end > json_start:
                json_str = response_text[json_start:json_end]
                food_data = json.loads(json_str)
                foods = food_data.get("foods", [])
            else:
                # Fallback: try to parse entire response as JSON
                food_data = json.loads(response_text)
                foods = food_data.get("foods", [])
        except json.JSONDecodeError:
            # If JSON parsing fails, try to extract food names from text
            logger.warning(
                "Failed to parse JSON from Gemini response, using fallback method"
            )
            foods = extract_food_names_from_text(response_text)

        logger.info(f"Foods detected: {foods}")
        return foods

    except Exception as e:
        logger.error(f"Error detecting foods with Gemini: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Food detection failed: {str(e)}")


def extract_food_names_from_text(text: str) -> List[str]:
    """Fallback method to extract food names from text"""
    # Common food words to look for
    common_foods = [
        "apple",
        "banana",
        "orange",
        "rice",
        "chicken",
        "beef",
        "pork",
        "fish",
        "bread",
        "pasta",
        "salad",
        "vegetables",
        "fruits",
        "eggs",
        "cheese",
        "yogurt",
        "milk",
        "potato",
        "tomato",
        "carrot",
        "broccoli",
        "spinach",
    ]

    text_lower = text.lower()
    detected_foods = []

    for food in common_foods:
        if food in text_lower:
            detected_foods.append(food)

    # If no common foods found, try to extract words that might be food names
    if not detected_foods:
        words = text_lower.split()
        # Simple heuristic: words that are not too short and not common stop words
        stop_words = {
            "the",
            "and",
            "or",
            "but",
            "in",
            "on",
            "at",
            "to",
            "for",
            "of",
            "with",
            "by",
            "a",
            "an",
        }
        detected_foods = [
            word for word in words if len(word) > 3 and word not in stop_words
        ][:5]

    return detected_foods if detected_foods else ["food"]


async def search_food_in_usda(food_name: str) -> Optional[Dict[str, Any]]:
    """Search for food in USDA FoodData Central API"""
    try:
        url = f"{USDA_BASE_URL}/foods/search"
        params = {
            "api_key": USDA_API_KEY,
            "query": food_name,
            "pageSize": 1,
            "dataType": ["Foundation", "SR Legacy"],
            "sortBy": "dataType.keyword",
            "sortOrder": "desc",
        }

        async with aiohttp.ClientSession() as session:
            async with session.get(url, params=params) as response:
                if response.status == 200:
                    data = await response.json()
                    foods = data.get("foods", [])
                    if foods:
                        return foods[0]  # Return the first match
                else:
                    logger.error(
                        f"USDA API error: {response.status} - {await response.text()}"
                    )

    except Exception as e:
        logger.error(f"Error searching USDA database for {food_name}: {str(e)}")

    return None


def extract_nutrition_info(food_data: Dict[str, Any]) -> NutritionInfo:
    """Extract nutrition information from USDA food data"""
    nutrition = NutritionInfo()

    # Get food nutrients
    nutrients = food_data.get("foodNutrients", [])

    # Map nutrient IDs to their names (common USDA nutrient IDs)
    nutrient_mapping = {
        1008: "calories",  # Energy
        1003: "protein",  # Protein
        1005: "carbs",  # Carbohydrate
        1004: "fat",  # Total lipid (fat)
        1079: "fiber",  # Fiber
        2000: "sugar",  # Sugars
        1093: "sodium",  # Sodium
        1253: "cholesterol",  # Cholesterol
    }

    for nutrient in nutrients:
        nutrient_id = nutrient.get("nutrientId")
        amount = nutrient.get("value", 0)

        if nutrient_id in nutrient_mapping:
            nutrient_name = nutrient_mapping[nutrient_id]
            if hasattr(nutrition, nutrient_name):
                setattr(nutrition, nutrient_name, amount)

    return nutrition


async def get_nutritional_info(food_name: str) -> Optional[FoodItem]:
    """Get complete nutritional information for a food item"""
    try:
        # Search in USDA database
        food_data = await search_food_in_usda(food_name)

        if food_data:
            nutrition = extract_nutrition_info(food_data)
            return FoodItem(name=food_name, nutrition=nutrition)
        else:
            # If not found in USDA, return basic info with zero values
            logger.warning(f"Food '{food_name}' not found in USDA database")
            return FoodItem(name=food_name, nutrition=NutritionInfo())

    except Exception as e:
        logger.error(f"Error getting nutritional info for {food_name}: {str(e)}")
        return FoodItem(name=food_name, nutrition=NutritionInfo())


@app.get("/")
async def root():
    """Root endpoint"""
    return {"message": "Nutrition Analysis API is running", "version": "1.0.0"}


@app.post("/analyze-food", response_model=NutritionResponse)
async def analyze_food_image(file: UploadFile = File(...)):
    """
    Analyze a food image and return nutritional information.

    - **file**: Image file (JPEG/PNG)
    - **Returns**: JSON with detected foods and their nutritional information
    """
    try:
        # Validate file type
        if not file.content_type or not file.content_type.startswith("image/"):
            raise HTTPException(status_code=400, detail="File must be an image")

        # Read image bytes
        image_bytes = await file.read()

        if len(image_bytes) == 0:
            raise HTTPException(status_code=400, detail="Empty image file")

        logger.info(f"Received image: {file.filename}, size: {len(image_bytes)} bytes")

        # Step 1: Detect foods using Gemini
        detected_foods = await detect_foods_with_gemini(image_bytes)

        if not detected_foods:
            return NutritionResponse(
                success=True, foods_detected=[], total_nutrition=NutritionInfo()
            )

        # Step 2: Get nutritional information for each food
        food_items = []
        total_nutrition = NutritionInfo()

        for food_name in detected_foods:
            food_item = await get_nutritional_info(food_name)
            if food_item:
                food_items.append(food_item)

                # Add to totals
                total_nutrition.calories += food_item.nutrition.calories
                total_nutrition.protein += food_item.nutrition.protein
                total_nutrition.carbs += food_item.nutrition.carbs
                total_nutrition.fat += food_item.nutrition.fat
                total_nutrition.fiber += food_item.nutrition.fiber
                total_nutrition.sugar += food_item.nutrition.sugar
                total_nutrition.sodium += food_item.nutrition.sodium
                total_nutrition.cholesterol += food_item.nutrition.cholesterol

        # Round totals to 2 decimal places
        total_nutrition.calories = round(total_nutrition.calories, 2)
        total_nutrition.protein = round(total_nutrition.protein, 2)
        total_nutrition.carbs = round(total_nutrition.carbs, 2)
        total_nutrition.fat = round(total_nutrition.fat, 2)
        total_nutrition.fiber = round(total_nutrition.fiber, 2)
        total_nutrition.sugar = round(total_nutrition.sugar, 2)
        total_nutrition.sodium = round(total_nutrition.sodium, 2)
        total_nutrition.cholesterol = round(total_nutrition.cholesterol, 2)

        return NutritionResponse(
            success=True, foods_detected=food_items, total_nutrition=total_nutrition
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error analyzing food image: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy", "service": "nutrition-analysis-api"}


if __name__ == "__main__":
    import uvicorn

    # Check if API keys are configured
    if not os.getenv("GEMINI_API_KEY"):
        logger.error("GEMINI_API_KEY not found in environment variables")
        exit(1)

    if not os.getenv("USDA_API_KEY"):
        logger.error("USDA_API_KEY not found in environment variables")
        exit(1)

    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
