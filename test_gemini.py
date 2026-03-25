import urllib.request, json, urllib.error, os

key = os.environ.get('GEMINI_API_KEY', '')
url = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent'

prompt = (
    "You are an expert Korean shortform scriptwriter.\n"
    "Generate exactly 3 scenes for topic: 커피의 효능\n"
    "Return ONLY valid JSON array. Each object: scene_id(int), narration(Korean 80-100chars), dalle_prompt(English Disney Pixar 3D animated style)\n"
)

body = json.dumps({
    'contents': [{'role': 'user', 'parts': [{'text': prompt}]}],
    'generationConfig': {'maxOutputTokens': 1500, 'temperature': 0.7}
}).encode('utf-8')

req = urllib.request.Request(
    url, data=body,
    headers={'x-goog-api-key': key, 'Content-Type': 'application/json'}
)

try:
    with urllib.request.urlopen(req, timeout=30) as r:
        res = json.loads(r.read())
        text = res['candidates'][0]['content']['parts'][0]['text'].strip()
        print("=== Gemini 2.5 Flash OK ===")
        print(text[:800])
        # JSON 파싱 시도
        if '```json' in text:
            s = text.index('```json') + 7
            e = text.index('```', s)
            text = text[s:e].strip()
        scenes = json.loads(text)
        print("\n=== 씬 수:", len(scenes))
        for sc in scenes:
            print(f"  씬 {sc['scene_id']}: {sc['narration'][:40]}... ({len(sc['narration'])}자)")
except urllib.error.HTTPError as e:
    print('FAIL', e.code, e.read().decode('utf-8')[:300])
