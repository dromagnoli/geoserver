.. _tutorial_imagemosaic_cog_landsat8:

ImageMosaic example with  Landsat 8 COG datasets
================================================


Introduction
------------

This tutorial provide some hints on configuring an ImageMosaic on top of some Landsast 8 COG datasets available through the `Registry of Open Data on AWS <https://registry.opendata.aws/landsat-8/>`_

Quoting from that page: *The data are organized using a directory structure based on each scene’s path and row. For instance, the files for Landsat scene LC08_L1TP_139045_20170304_20170316_01_T1 are available in the following location: s3://landsat-pds/c1/L8/139/045/LC08_L1TP_139045_20170304_20170316_01_T1/*

Data have each band on a separate file so we will need to use :ref:`coverage_views` in order to aggregate them on a single 12 band coverages.


ImageMosaic Configuration files
------------------------------- 

We need a couple of configuration files to have an ImageMosaic propertly set. Configuration is based on these key points:

* Each of the 12 bands is stored on a separate file. We plan to expose the dataset collection as a single coverage view (a view on top of these 12 bands) so we need to extract a coverage name from the dataset file, using a CoverageNameCollector.
* Scenes belong to different UTM zones so we need to leverage on heterogeneous CRS support.
* The ImageMosaic will be initially created empty without any data. Data will be harvested as a second step.
* A Time dimension will be enabled, based on the acquisition date reported in the filename

More details on ImageMosaic configuration are available on the dedicated documentation section: :ref:`data_imagemosaic_config`

Based on the above key points, we can setup the following configuration files:

indexer.properties:
"""""""""""""""""""

This contains the main configuration to index the datasets composing the ImageMosaic. 

.. include:: src/indexer.properties
   :literal:
   
Relevant parts :

* Cog flag Specifying the ImageMosaic is a Mosaic of COG Datasets
* CoverageNameCollectorSpi needed to extact a coverageName (to be lately used by coverageView mechanism) from the filename
* PropertyCollectors, TimeAttribute and Schema are used to defined the ImageMosaic index columns and how to populate them
* HeterogeneousCRS, MosaicCRS, GranuleAcceptors and GranuleHandler are the properties needed to support heterogeneous CRS and have them properly handled. See :ref:`multi-crs-mosaic` for more details.
* CanBeEmpty allows to define an empty ImageMosaic. It will be populated afterwards


timeregex.properties:
"""""""""""""""""""""

The previous indexer refers to a time dimension and the related time column in the index's schema which get populated by extracting the time value from the filename (the first set of 8 digits, representing YEAR, MONTH, DAY) using the regex specified in the timeregex.properties file.

.. include:: src/timeregex.properties
   :literal:

datastore.properties:
"""""""""""""""""""""

Due to the amount of available datasets, it's better to store the ImageMosaic index on a DBMS, i.e. a PostGIS DB. See :ref:`mosaic_datastore_properties` for more info. 

.. include:: src/datastore.properties
   :literal:

Once the 3 files have been created, create a zip archive with them. Let's name it Landsat8.zip. (Note: the files need to be in the root of the zip files, not into a subdirectory)

You are now ready to use REST calls to start the ImageMosaic creation.

ImageMosaic Store creation via REST
"""""""""""""""""""""""""""""""""""
On the next steps we assume a workspace named "test" exists and REST credentials are user=admin password=geoserver. Update them accordingly based on your installation. Moreover, make sure a default aws region is defined on JAVA System Property, using the flag -Diio.https.aws.region=us-west-2

**Create an empty ImageMosaic without configuring it**

* Request*

.. admonition:: curl

   ::

       curl -u admin:geoserver -XPUT --write-out %{http_code} -H "Content-type:application/zip" --data-binary @Landsat8.zip http://localhost:8080/geoserver/rest/workspaces/test/coveragestores/landsat8/file.imagemosaic?configure=none

*Response*

::

   201 OK

**Providing sample prototyping granules**

Next step is providing a prototype dataset for each band to be supported. Let's suppose we want to support all of the twelve bands (B1 to B12) to be lately selected through a style containing a channel select.

.. admonition:: curl

   ::

       curl -u admin:geoserver -XPOST -H "Content-type: text/plain" --write-out %{http_code} -d "https://landsat-pds.s3.amazonaws.com/c1/L8/007/008/LC08_L1GT_007008_20170814_20170814_01_RT/LC08_L1GT_007008_20170814_20170814_01_RT_B1.TIF" "http://localhost:8080/geoserver/rest/workspaces/test/coveragestores/landsat8/remote.imagemosaic"

*Response*

::

   202 Accepted

And repeat from B2 to B12 (or just add the only bands you want to be supported).

   curl -u admin:geoserver -XPOST -H "Content-type: text/plain" --write-out %{http_code} -d "https://landsat-pds.s3.amazonaws.com/c1/L8/007/008/LC08_L1GT_007008_20170814_20170814_01_RT/LC08_L1GT_007008_20170814_20170814_01_RT_B2.TIF" "http://localhost:8080/geoserver/rest/workspaces/test/coveragestores/landsat8/remote.imagemosaic"
   
   curl -u admin:geoserver -XPOST -H "Content-type: text/plain" --write-out %{http_code} -d "https://landsat-pds.s3.amazonaws.com/c1/L8/007/008/LC08_L1GT_007008_20170814_20170814_01_RT/LC08_L1GT_007008_20170814_20170814_01_RT_B3.TIF" "http://localhost:8080/geoserver/rest/workspaces/test/coveragestores/landsat8/remote.imagemosaic"
   
   ...
  
**Initializing the store (Listing available coverages)**
  
Once a prototype has been provided for each band, we need to initialize the store by querying it for the available coverages.


.. admonition:: curl

   ::

       curl -v -u admin:geoserver -XGET http://localhost:8080/geoserver/rest/workspaces/test/coveragestores/landsat8/coverages.xml?list=all

*Response*

.. code-block:: xml

       <List>
           <coverageName>B2</coverageName>
           <coverageName>B3</coverageName>
           <coverageName>B10</coverageName>
           <coverageName>B4</coverageName>
           <coverageName>B11</coverageName>
           <coverageName>B5</coverageName>
           <coverageName>B6</coverageName>
           <coverageName>B7</coverageName>
           <coverageName>B8</coverageName>
           <coverageName>B9</coverageName>
           <coverageName>B1</coverageName>
      </list>


TO BE CONTINUED