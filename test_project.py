import urllib.request, json, time

# 새 프로젝트 생성
url = 'http://localhost:8080/api/script/generate'
body = json.dumps({'topic': '커피의 효능', 'sceneCount': 3, 'outputPlatform': 'YOUTUBE_SHORTS'}).encode('utf-8')
req = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(req, timeout=15) as r:
    res = json.loads(r.read())
    pid = res['data']['id']
    print('Project ID:', pid)

# 상태 폴링
for i in range(30):
    time.sleep(5)
    with urllib.request.urlopen('http://localhost:8080/api/projects/' + str(pid), timeout=10) as r:
        d = json.loads(r.read())['data']
        status = d['status']
        images = d.get('imageCount', 0)
        subs = len(d.get('subtitles', []))
        elapsed = (i+1)*5
        print(str(elapsed) + 's status=' + status + ' images=' + str(images) + ' subs=' + str(subs))
        if status not in ('PARSING', 'PROCESSING'):
            print('완료! 최종 상태:', status)
            # 이미지 소스 확인
            for m in d.get('mediaItems', []):
                src = m.get('sourceUrl', 'None')
                print('  이미지 ' + str(m.get('orderIndex', 0)+1) + ': ' + str(src)[:60])
            break

