FROM mcr.microsoft.com/dotnet/sdk as build-image

WORKDIR /home/app

COPY ./*.sln ./
COPY ./*/*.csproj ./
RUN for file in $(ls *.csproj); do mkdir -p ./${file%.*}/ && mv $file ./${file%.*}/; done

RUN dotnet restore

COPY . .

RUN dotnet test --verbosity=normal --results-directory /TestResults/ --logger "trx;LogFileName=test_results.xml" ./Tests/Tests.csproj

RUN dotnet publish ./AccountOwnerServer/AccountOwnerServer.csproj -o /publish/

FROM mcr.microsoft.com/dotnet/aspnet

WORKDIR /publish

COPY --from=build-image /publish .

COPY --from=build-image /TestResults /TestResults

ENV TEAMCITY_PROJECT_NAME = ${TEAMCITY_PROJECT_NAME}

ENTRYPOINT ["dotnet", "AccountOwnerServer.dll"]
