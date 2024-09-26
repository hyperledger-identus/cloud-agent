import json
import requests


AGENT_URL = "http://localhost:8085"

HOLDER_LONG_FORM_DID = "did:prism:73196107e806b084d44339c847a3ae8dd279562f23895583f62cc91a2ee5b8fe:CnsKeRI8CghtYXN0ZXItMBABSi4KCXNlY3AyNTZrMRIhArrplJNfQYxthryRU87XdODy-YWUh5mqrvIfAdoZFeJBEjkKBWtleS0wEAJKLgoJc2VjcDI1NmsxEiEC8rsFplfYvRLazdWWi3LNR1gaAQXb-adVhZacJT4ntwE"
HOLDER_ASSERTION_PRIVATE_KEY_HEX = (
    "2902637d412190fb08f5d0e0b2efc1eefae8060ae151e7951b69afbecbdd452e"
)
JWT_VC = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NksifQ.eyJpc3MiOiJkaWQ6cHJpc206ZmZkN2NmNWEwYjczYzgyZTFmMTZiNDFlMWFmY2Q4YzliMGI4N2NhNGYyMmMxMTMyOGVhY2Y1NDZiNjExYjFmYyIsInN1YiI6ImRpZDpwcmlzbTo3MzE5NjEwN2U4MDZiMDg0ZDQ0MzM5Yzg0N2EzYWU4ZGQyNzk1NjJmMjM4OTU1ODNmNjJjYzkxYTJlZTViOGZlOkNuc0tlUkk4Q2dodFlYTjBaWEl0TUJBQlNpNEtDWE5sWTNBeU5UWnJNUkloQXJycGxKTmZRWXh0aHJ5UlU4N1hkT0R5LVlXVWg1bXFydklmQWRvWkZlSkJFamtLQld0bGVTMHdFQUpLTGdvSmMyVmpjREkxTm1zeEVpRUM4cnNGcGxmWXZSTGF6ZFdXaTNMTlIxZ2FBUVhiLWFkVmhaYWNKVDRudHdFIiwibmJmIjoxNzI3MzQwNTYyLCJ2YyI6eyJjcmVkZW50aWFsU3ViamVjdCI6eyJmaXJzdE5hbWUiOiJBbGljZSIsImdyYWRlIjozLjIsImRlZ3JlZSI6IkNoZW1pY2FsRW5naW5lZXJpbmciLCJpZCI6ImRpZDpwcmlzbTo3MzE5NjEwN2U4MDZiMDg0ZDQ0MzM5Yzg0N2EzYWU4ZGQyNzk1NjJmMjM4OTU1ODNmNjJjYzkxYTJlZTViOGZlOkNuc0tlUkk4Q2dodFlYTjBaWEl0TUJBQlNpNEtDWE5sWTNBeU5UWnJNUkloQXJycGxKTmZRWXh0aHJ5UlU4N1hkT0R5LVlXVWg1bXFydklmQWRvWkZlSkJFamtLQld0bGVTMHdFQUpLTGdvSmMyVmpjREkxTm1zeEVpRUM4cnNGcGxmWXZSTGF6ZFdXaTNMTlIxZ2FBUVhiLWFkVmhaYWNKVDRudHdFIn0sInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJVbml2ZXJzaXR5RGVncmVlQ3JlZGVudGlhbCJdLCJAY29udGV4dCI6WyJodHRwczpcL1wvd3d3LnczLm9yZ1wvMjAxOFwvY3JlZGVudGlhbHNcL3YxIl0sImlzc3VlciI6ImRpZDpwcmlzbTpmZmQ3Y2Y1YTBiNzNjODJlMWYxNmI0MWUxYWZjZDhjOWIwYjg3Y2E0ZjIyYzExMzI4ZWFjZjU0NmI2MTFiMWZjIn19.dVPyCW_-Q6iO-EYIvRRrIYddebw6KJtSaeeFolyKznaCkDoY_X9LKxVU-xY6YO1xwbV6dnXHXsvcey3SR5oVng"


def submit_vp_response():
    ps = {
        "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
        "definition_id": "3e216a58-2118-45ea-8db0-8798d01bb252",
        "descriptor_map": [
            {
                "id": "university_degree",
                "format": "jwt_vc",
                "path": "$",
            }
        ],
    }
    resp = requests.post(
        f"{AGENT_URL}/oid4vp/submissions",
        data={"vp_token": JWT_VC, "presentation_submission": json.dumps(ps)},
    )

    print(resp.status_code)
    print(resp.text)


if __name__ == "__main__":
    submit_vp_response()
