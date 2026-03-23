import requests
import sys
from datetime import datetime
import json

class WhatsupAPITester:
    def __init__(self, base_url="https://event-finder-92.preview.emergentagent.com"):
        self.base_url = base_url
        self.api_url = f"{base_url}/api"
        self.session_token = None
        self.user_id = None
        self.tests_run = 0
        self.tests_passed = 0

    def run_test(self, name, method, endpoint, expected_status, data=None, headers=None):
        """Run a single API test"""
        url = f"{self.api_url}/{endpoint}"
        test_headers = {'Content-Type': 'application/json'}
        
        if headers:
            test_headers.update(headers)
            
        if self.session_token:
            test_headers['Authorization'] = f'Bearer {self.session_token}'

        self.tests_run += 1
        print(f"\n🔍 Testing {name}...")
        print(f"   URL: {url}")
        
        try:
            if method == 'GET':
                response = requests.get(url, headers=test_headers)
            elif method == 'POST':
                response = requests.post(url, json=data, headers=test_headers)
            elif method == 'DELETE':
                response = requests.delete(url, headers=test_headers)

            success = response.status_code == expected_status
            if success:
                self.tests_passed += 1
                print(f"✅ Passed - Status: {response.status_code}")
                try:
                    response_data = response.json()
                    if isinstance(response_data, list):
                        print(f"   Response: List with {len(response_data)} items")
                    elif isinstance(response_data, dict):
                        print(f"   Response keys: {list(response_data.keys())}")
                except:
                    print(f"   Response: {response.text[:100]}...")
            else:
                print(f"❌ Failed - Expected {expected_status}, got {response.status_code}")
                try:
                    error_data = response.json()
                    print(f"   Error: {error_data}")
                except:
                    print(f"   Error text: {response.text[:200]}")

            return success, response.json() if response.headers.get('content-type', '').startswith('application/json') else response.text

        except Exception as e:
            print(f"❌ Failed - Error: {str(e)}")
            return False, {}

    def test_health_check(self):
        """Test health endpoint"""
        return self.run_test("Health Check", "GET", "health", 200)

    def test_categories(self):
        """Test categories endpoint"""
        return self.run_test("Get Categories", "GET", "categories", 200)

    def test_seed_events(self):
        """Seed mock events"""
        return self.run_test("Seed Events", "POST", "seed-events", 200)

    def test_get_events(self):
        """Test get events without filters"""
        return self.run_test("Get All Events", "GET", "events", 200)

    def test_get_events_with_filters(self):
        """Test events with various filters"""
        filters = [
            ("events?date_filter=today", "Today's Events"),
            ("events?date_filter=tomorrow", "Tomorrow's Events"),
            ("events?category=music", "Music Events"),
            ("events?is_free=true", "Free Events"),
            ("events?lat=41.9028&lng=12.4964&distance_km=10", "Events near Rome (10km)"),
        ]
        
        results = []
        for endpoint, name in filters:
            success, data = self.run_test(name, "GET", endpoint, 200)
            results.append(success)
        
        return all(results)

    def test_get_single_event(self, event_id="evt_roma_001"):
        """Test get single event"""
        return self.run_test(f"Get Event {event_id}", "GET", f"events/{event_id}", 200)

    def test_auth_endpoints_without_token(self):
        """Test auth endpoints without authentication"""
        endpoints = [
            ("auth/me", "Get Current User"),
            ("user/saved-events", "Get Saved Events"),
            ("user/rsvps", "Get User RSVPs"),
        ]
        
        results = []
        for endpoint, name in endpoints:
            success, data = self.run_test(f"{name} (Unauthenticated)", "GET", endpoint, 401)
            results.append(success)
        
        return all(results)

    def test_rsvp_without_auth(self, event_id="evt_roma_001"):
        """Test RSVP without authentication"""
        return self.run_test("RSVP without Auth", "POST", f"events/{event_id}/rsvp", 401)

    def test_save_event_without_auth(self, event_id="evt_roma_001"):
        """Test save event without authentication"""
        return self.run_test("Save Event without Auth", "POST", f"events/{event_id}/save", 401)

    def create_test_session(self):
        """Create a test session using the auth testing guide approach"""
        print("\n🔧 Creating test session...")
        
        # For testing purposes, we'll create a mock session token
        # In a real scenario, this would go through the OAuth flow
        timestamp = int(datetime.now().timestamp())
        self.session_token = f"test_session_{timestamp}"
        self.user_id = f"test_user_{timestamp}"
        
        print(f"   Test session token: {self.session_token}")
        print(f"   Test user ID: {self.user_id}")
        
        # Note: In a real test, you'd need to insert this into the database
        # For now, we'll just test the endpoints that don't require auth
        return True

    def test_event_status_endpoints(self, event_id="evt_roma_001"):
        """Test event status endpoints (RSVP and save status)"""
        endpoints = [
            (f"events/{event_id}/rsvp-status", "RSVP Status"),
            (f"events/{event_id}/save-status", "Save Status"),
        ]
        
        results = []
        for endpoint, name in endpoints:
            success, data = self.run_test(name, "GET", endpoint, 200)
            results.append(success)
        
        return all(results)

    def test_admin_stats(self):
        """Test admin stats endpoint"""
        return self.run_test("Admin Stats", "GET", "admin/stats", 200)

    def test_admin_cities(self):
        """Test admin cities endpoint"""
        return self.run_test("Admin Cities", "GET", "admin/cities", 200)

    def test_discovery_status(self):
        """Test discovery job status endpoint"""
        return self.run_test("Discovery Status", "GET", "jobs/discovery/status", 200)

    def test_discovery_review_queue(self):
        """Test discovery review queue endpoint"""
        return self.run_test("Discovery Review Queue", "GET", "discovery/review-queue", 200)

    def test_discovery_events(self):
        """Test discovery events endpoint"""
        return self.run_test("Discovery Events", "GET", "discovery/events", 200)

    def test_discovery_run_job(self):
        """Test running discovery job"""
        data = {
            "cities": ["Roma"],
            "max_queries": 2,
            "max_pages": 3
        }
        return self.run_test("Run Discovery Job", "POST", "jobs/discovery/run", 200, data=data)

    def test_discovery_dedup_check(self):
        """Test deduplication check endpoint"""
        return self.run_test("Dedup Check", "GET", "discovery/dedup-check?title=Test Event&date=2024-01-01&city=Roma", 200)

def main():
    print("🚀 Starting Whatsup API Testing...")
    print("=" * 50)
    
    tester = WhatsupAPITester()
    
    # Test basic endpoints
    print("\n📋 Testing Basic Endpoints...")
    tester.test_health_check()
    tester.test_categories()
    
    # Seed events
    print("\n🌱 Seeding Events...")
    tester.test_seed_events()
    
    # Test events endpoints
    print("\n📅 Testing Events Endpoints...")
    tester.test_get_events()
    tester.test_get_events_with_filters()
    tester.test_get_single_event()
    
    # Test event status endpoints (these should work without auth)
    print("\n📊 Testing Event Status Endpoints...")
    tester.test_event_status_endpoints()
    
    # Test auth-protected endpoints without authentication
    print("\n🔒 Testing Auth-Protected Endpoints (Unauthenticated)...")
    tester.test_auth_endpoints_without_token()
    tester.test_rsvp_without_auth()
    tester.test_save_event_without_auth()
    
    # Test admin endpoints
    print("\n👑 Testing Admin Endpoints...")
    tester.test_admin_stats()
    tester.test_admin_cities()
    
    # Test discovery endpoints
    print("\n🔍 Testing Discovery Endpoints...")
    tester.test_discovery_status()
    tester.test_discovery_review_queue()
    tester.test_discovery_events()
    tester.test_discovery_dedup_check()
    
    # Test discovery job (this might take longer)
    print("\n🚀 Testing Discovery Job...")
    print("   Note: This test may take 30-60 seconds due to AI processing...")
    tester.test_discovery_run_job()
    
    # Print results
    print("\n" + "=" * 50)
    print(f"📊 Test Results: {tester.tests_passed}/{tester.tests_run} passed")
    
    if tester.tests_passed == tester.tests_run:
        print("🎉 All tests passed!")
        return 0
    else:
        print(f"⚠️  {tester.tests_run - tester.tests_passed} tests failed")
        return 1

if __name__ == "__main__":
    sys.exit(main())