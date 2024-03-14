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

Acesse: http://localhost:5002 to see registered events and submit proposals

Admin em http://localhost:5002/admin user:admin pass:1234

API em http://localhost:5002/apidocs/


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
```

### Run as Container

Now that we have the application running locally, let's run it as a container.

# Build a Dockerfile for the application

We will need to create the dockerfile with the following:

1. Base image - python:3.6, install dependencies

2. Test our application - run tests with image "qnib/pytest"
Run this command
    ```bash
    pytest -v --cov-config .coveragerc --cov=talkshow -l --tb=short --maxfail=1 tests/
    ```

3. Run the application.
    ```
        CMD ["flask", "run"]
    ```

4. Build the docker image with tag "multistage_workshop"

5. Run the application.

### Please think of the following:

1. What is the base image you are going to use?
2. What are the dependencies you need to install?
3. What is the command to run the tests?
4. What is the command to run the application?
5. How to pass the USERNAME and PASSWORD to the application?
6. How to reach my application through local machine browser?
7. How to shrink size for the final image? did we need to install the test dependencies in the final image?


Finished the LAB? confidence?, push the changes to your repository and create a pull request, send the Pull Request link to me.

Now that you have Dockerized your application, create new Job in Jenkins to build and push the image to DockerHub.

### Jenkinsfile

There are three ways to achive the application running goals.

# Please choose one of the following:
- Not multistage build and try to improve it with docker commands
- Only using docker command
- Using Docker plugin to build push and run the application


1. Create a new Jenkinsfile in the root of your repository
2. Jenkins file should have minimal stages:
    - Checkout SCM
    - Build the Docker image
    - Push the Docker image to DockerHub
    - Run the Application

3. Create a new Job in Jenkins
4. Configure the Job to use the Jenkinsfile
5. Run the Job
6. Check the image in DockerHub and see if it was pushed successfully
7. Send the Jenkins Job link to me


## Shared Libraries (Bonus)

Create a new Shared library in Jenkins to use the Jenkinsfile in all the repositories.
The shared library should have the following:

1. "call" function that accepting the repository uri and the branch name
2. "call" function should checkout the branch repository with "Git" plugin
3. Use the shared library in the jenkins file and replace the first SCM step with calling to the shared library function and pass the arguments "URI" and "BRANCH_NAME".

Good luck!

