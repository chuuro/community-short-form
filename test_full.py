import urllib.request, json, time, urllib.error

# 새 프로젝트 생성
url = 'http://localhost:8080/api/script/generate'
body = json.dumps({'topic': '커피의 효능', 'sceneCount': 3, 'outputPlatform': 'YOUTUBE_SHORTS'}).encode('utf-8')
req = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'})
try:
    with urllib.request.urlopen(req, timeout=15) as r:
        res = json.loads(r.read())
        pid = res['data']['id']
        print('Project ID:', pid)
except Exception as e:
    print('API 호출 실패:', e)
    exit(1)

for i in range(40):
    time.sleep(5)
    try:
        with urllib.request.urlopen('http://localhost:8080/api/projects/' + str(pid), timeout=10) as r:
            d = json.loads(r.read())['data']
            status = d['status']
            images = d.get('imageCount', 0)
            subs = len(d.get('subtitles', []))
            elapsed = (i+1)*5
            print(str(elapsed) + 's status=' + status + ' images=' + str(images) + ' subs=' + str(subs))
            if status not in ('PARSING', 'PROCESSING'):
                print('\n=== 최종 결과 ===')
                print('상태:', status)
                for s in d.get('subtitles', []):
                    print('나레이션 ' + str(s['orderIndex']+1) + ' [' + str(len(s['content'])) + 'chars]: ' + s['content'][:60] + '...')
                print()
                for m in d.get('mediaItems', []):
                    src = m.get('sourceUrl', 'None')
                    print('이미지 ' + str(m.get('orderIndex', 0)+1) + ': ' + str(src)[:70])
                break
    except Exception as e:
        print('폴링 오류:', e)
