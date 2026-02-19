#!/usr/bin/env python3
"""
YouTube URL Resolver Service
Runs yt-dlp to extract direct video/audio URLs for ExoPlayer.

Usage:
    pip install flask yt-dlp
    python resolver_server.py

The server listens on port 8765 by default.
"""

import subprocess
import json
import time
from flask import Flask, jsonify, request
from functools import lru_cache

app = Flask(__name__)

# Cache results for 45 seconds (YouTube URLs expire in ~6 hours, but better to be safe)
CACHE_TTL = 45
_cache = {}

def get_cached(video_id):
    """Get cached result if still valid."""
    if video_id in _cache:
        result, timestamp = _cache[video_id]
        if time.time() - timestamp < CACHE_TTL:
            return result
        del _cache[video_id]
    return None

def set_cached(video_id, result):
    """Cache a result."""
    _cache[video_id] = (result, time.time())

def resolve_youtube(video_id):
    """Use yt-dlp to get direct URLs for a YouTube video."""
    
    # Check cache first
    cached = get_cached(video_id)
    if cached:
        return cached
    
    url = f"https://www.youtube.com/watch?v={video_id}"
    
    try:
        # Get JSON info with format details
        result = subprocess.run(
            [
                "yt-dlp",
                "-j",  # JSON output
                "--no-warnings",
                url
            ],
            capture_output=True,
            text=True,
            timeout=30
        )
        
        if result.returncode != 0:
            return {"error": f"yt-dlp failed: {result.stderr}"}
        
        info = json.loads(result.stdout)
        
        # Find best video and audio formats
        formats = info.get("formats", [])
        
        # Filter for formats with direct URLs
        video_formats = [f for f in formats if f.get("vcodec") != "none" and f.get("url")]
        audio_formats = [f for f in formats if f.get("acodec") != "none" and f.get("vcodec") == "none" and f.get("url")]
        
        # Also look for combined formats (has both video and audio)
        combined_formats = [f for f in formats if f.get("vcodec") != "none" and f.get("acodec") != "none" and f.get("url")]
        
        response = {
            "video_id": video_id,
            "title": info.get("title", ""),
            "duration": info.get("duration", 0),
            "timestamp": int(time.time()),
            "expires_in": CACHE_TTL
        }
        
        # Prefer combined format if available (simpler for player)
        if combined_formats:
            # Sort by quality (height), prefer mp4
            combined_formats.sort(key=lambda f: (
                f.get("height", 0),
                1 if f.get("ext") == "mp4" else 0
            ), reverse=True)
            
            # Pick 720p or closest
            best = None
            for fmt in combined_formats:
                if fmt.get("height", 0) <= 720:
                    best = fmt
                    break
            if not best:
                best = combined_formats[-1]  # Lowest quality if all > 720p
            
            response["type"] = "combined"
            response["url"] = best.get("url")
            response["quality"] = f"{best.get('height', '?')}p"
            response["format"] = best.get("ext", "mp4")
        
        # Otherwise, provide separate video + audio
        elif video_formats and audio_formats:
            # Sort video by height, prefer mp4
            video_formats.sort(key=lambda f: (
                f.get("height", 0),
                1 if f.get("ext") == "mp4" else 0
            ), reverse=True)
            
            # Pick 720p or closest
            best_video = None
            for fmt in video_formats:
                if fmt.get("height", 0) <= 720:
                    best_video = fmt
                    break
            if not best_video:
                best_video = video_formats[-1]
            
            # Sort audio by bitrate
            audio_formats.sort(key=lambda f: f.get("abr", 0) or f.get("tbr", 0), reverse=True)
            best_audio = audio_formats[0]
            
            response["type"] = "separate"
            response["video_url"] = best_video.get("url")
            response["audio_url"] = best_audio.get("url")
            response["video_quality"] = f"{best_video.get('height', '?')}p"
            response["audio_quality"] = f"{best_audio.get('abr', '?')}kbps"
        
        else:
            return {"error": "No suitable formats found"}
        
        # Cache the result
        set_cached(video_id, response)
        
        return response
        
    except subprocess.TimeoutExpired:
        return {"error": "yt-dlp timed out"}
    except json.JSONDecodeError as e:
        return {"error": f"Failed to parse yt-dlp output: {e}"}
    except Exception as e:
        return {"error": str(e)}


@app.route("/resolve/<video_id>")
def resolve(video_id):
    """Resolve a YouTube video ID to direct URLs."""
    result = resolve_youtube(video_id)
    
    if "error" in result:
        return jsonify(result), 500
    
    return jsonify(result)


@app.route("/health")
def health():
    """Health check endpoint."""
    return jsonify({"status": "ok", "service": "youtube-resolver"})


@app.route("/")
def index():
    """API documentation."""
    return jsonify({
        "service": "YouTube URL Resolver",
        "endpoints": {
            "/resolve/<video_id>": "Resolve YouTube video to direct URLs",
            "/health": "Health check"
        },
        "example": "/resolve/dQw4w9WgXcQ",
        "response_types": {
            "combined": "Single URL with video+audio (preferred)",
            "separate": "Separate video_url and audio_url (requires MergingMediaSource)"
        }
    })


if __name__ == "__main__":
    print("YouTube Resolver Service")
    print("=" * 40)
    print("Endpoints:")
    print("  GET /resolve/<video_id> - Resolve YouTube video")
    print("  GET /health - Health check")
    print("=" * 40)
    
    # Run on all interfaces so Fire TV can reach it
    app.run(host="0.0.0.0", port=8765, debug=False)
