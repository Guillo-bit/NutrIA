#!/usr/bin/env python3
"""
Test script for the Nutrition Analysis API
"""
import requests
import json
import sys

def test_api_health():
    """Test if the API is running"""
    try:
        response = requests.get("http://localhost:8000/health")
        if response.status_code == 200:
            print("✅ API is healthy and running")
            return True
        else:
            print(f"❌ Health check failed: {response.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        print("❌ Could not connect to API. Make sure it's running on http://localhost:8000")
        return False

def test_analyze_food_endpoint():
    """Test the analyze-food endpoint with a sample image"""
    # Create a simple test image (1x1 pixel JPEG)
    # In a real test, you would use an actual food image
    test_image_data = b'\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.\' ",#\x1c\x1c(7),01444\x1f\'9=82<.342\xff\xc0\x00\x11\x08\x00\x01\x00\x01\x01\x01\x11\x00\x02\x11\x01\x03\x11\x01\xff\xc4\x00\x14\x00\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x08\xff\xc4\x00\x14\x10\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\xff\xda\x00\x0c\x03\x01\x00\x02\x11\x03\x11\x00\x3f\x00\xaa\xff\xd9'
    
    try:
        files = {'file': ('test_image.jpg', test_image_data, 'image/jpeg')}
        response = requests.post("http://localhost:8000/analyze-food", files=files)
        
        print(f"Response status: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("✅ Successfully analyzed image")
            print("Response structure:")
            print(json.dumps(data, indent=2))
            
            # Validate response structure
            required_fields = ['success', 'foods_detected', 'total_nutrition']
            if all(field in data for field in required_fields):
                print("✅ Response has all required fields")
                
                if data['foods_detected']:
                    print(f"✅ Detected {len(data['foods_detected'])} foods")
                    for food in data['foods_detected']:
                        print(f"  - {food['name']}: {food['nutrition']['calories']} calories")
                else:
                    print("ℹ️  No foods detected (this might be expected with test image)")
                    
                total = data['total_nutrition']
                print(f"✅ Total nutrition: {total['calories']} calories, {total['protein']}g protein, {total['carbs']}g carbs, {total['fat']}g fat")
                
                return True
            else:
                print("❌ Response missing required fields")
                return False
        else:
            print(f"❌ API request failed: {response.status_code}")
            print(f"Response: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error testing API: {str(e)}")
        return False

def main():
    """Main test function"""
    print("🧪 Testing Nutrition Analysis API")
    print("=" * 50)
    
    # Test health endpoint
    if not test_api_health():
        return 1
    
    print()
    
    # Test analyze-food endpoint
    if not test_analyze_food_endpoint():
        return 1
    
    print("\n✅ All tests passed!")
    return 0

if __name__ == "__main__":
    sys.exit(main())