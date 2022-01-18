#
# Copyright (c) 2021 Airbyte, Inc., all rights reserved.
#

from typing import List

from airbyte_api_client.api import destination_definition_api, source_definition_api

from .base import BaseListing


class SourceConnectorsDefinitions(BaseListing):
    api = source_definition_api.SourceDefinitionApi
    fields_to_display = ["name", "dockerRepository", "dockerImageTag", "sourceDefinitionId"]
    list_field_in_response = "sourceDefinitions"

    def get_listing(self) -> List[List[str]]:
        api_response = self.api.list_latest_source_definitions(self.api_instance, **self.COMMON_API_CALL_KWARGS)
        return self._parse_response(api_response)


class DestinationConnectorsDefinitions(BaseListing):
    api = destination_definition_api.DestinationDefinitionApi
    fields_to_display = ["name", "dockerRepository", "dockerImageTag", "destinationDefinitionId"]
    list_field_in_response = "destinationDefinitions"

    def get_listing(self) -> List[List[str]]:
        api_response = self.api.list_latest_destination_definitions(self.api_instance, **self.COMMON_API_CALL_KWARGS)
        return self._parse_response(api_response)
