A Call 4 Papers System - A simple base app as example of Flask Architecture

This is the material from the Flask Conf 2018 tutorial - The event took place on 08/24/2018 - Sponsored by SciELO

handout
The Handout in PDF contains a detailed explanation of the files in this repository, it is recommended to accompany the handout while developing the project and use this repository only as a reference for copy/paste.

Handout: https://github.com/rochacbruno/talkshow/blob/master/TutorialFlaskConf2018_BrunoRocha.pdf

Code to accompany the handout is on the master branch https://github.com/rochacbruno/talkshow/tree/master

IMPORTANT: Changes and developments in these applications will only be made in this extended branch, the master branch will remain in sync with the handout.

TL:DR;
Don't want to follow the handout and just see the code running?

```
git clone https://github.com/micha-bitton/ci-cd.git
cd advance/workshop/
python3.6 -m venv venv
source venv/bin/activate
pip install -e '.[dev]'

#comandos
flask adduser -u admin -p 1234
flask addevent -n "Flask Conf" -d "2018-08-25"
flask routes
flask run
```

Acesse: http://localhost:5000 to see registered events and submit proposals

Admin em http://localhost:5000/admin user:admin pass:1234

API em http://localhost:5000/apidocs/


URLS e APIS:

```bash
$ flask routes
Endpoint                   Methods    Rule
-------------------------  ---------  -----------------------------------------
admin.index                GET        /admin/
admin.static               GET        /admin/static/<path:filename>
bootstrap.static           GET        /static/bootstrap/<path:filename>
flasgger.<lambda>          GET        /apidocs/index.html
flasgger.apidocs           GET        /apidocs/
flasgger.apispec_1         GET        /apispec_1.json
flasgger.static            GET        /flasgger_static/<path:filename>
proposalview.action_view   POST       /admin/proposalview/action/
proposalview.ajax_lookup   GET        /admin/proposalview/ajax/lookup/
proposalview.ajax_update   POST       /admin/proposalview/ajax/update/
proposalview.create_view   GET, POST  /admin/proposalview/new/
proposalview.delete_view   POST       /admin/proposalview/delete/
proposalview.details_view  GET        /admin/proposalview/details/
proposalview.edit_view     GET, POST  /admin/proposalview/edit/
proposalview.export        GET        /admin/proposalview/export/<export_type>/
proposalview.index_view    GET        /admin/proposalview/
restapi.event              GET, POST  /api/v1/event/
restapi.eventitem          GET, POST  /api/v1/event/<event_id>
simplelogin.login          GET, POST  /login/
simplelogin.logout         GET        /logout/
static                     GET        /static/<path:filename>
webui.event                GET, POST  /<slug>/
webui.index                GET        /

