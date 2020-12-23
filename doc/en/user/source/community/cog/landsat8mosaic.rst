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

More details on ImageMosaic configuration are available on the dedicated documentation section: :ref:`_data_imagemosaic_config`

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
On these steps we assume a workspace named "test" exists and admin credentials are user=admin password=geoserver. Update them accordingly based on your installation.

* Create an empty ImageMosaic without configuring it*
*Request*

.. admonition:: curl

   ::

       curl -u admin:geoserver -XPUT --write-out %{http_code} -H "Content-type:application/zip" --data-binary @Landsat8.zip http://localhost:8080/geoserver/rest/workspaces/test/coveragestores/landsat8/file.imagemosaic?configure=none

*Response*

::

   200 OK

Updating an image mosaic contents












curl -u admin:Geos -XPUT --write-out %{http_code} -H "Content-type:application/zip" --data-binary @Landsat8.zip http://localhost:8084/geoserver/rest/workspaces/test/coveragestores/landsat8/file.imagemosaic?configure=none


.. code-block:: xml

	<Dimension name="time" default="current" units="ISO8601">
		2013-03-10T00:00:00.000Z,2013-03-11T00:00:00.000Z,2013-03-12T00:00:00.000Z,2013-03-13T00:00:00.000Z,2013-03-14T00:00:00.000Z,2013-03-15T00:00:00.000Z,2013-03-16T00:00:00.000Z,2013-03-17T00:00:00.000Z,2013-03-18T00:00:00.000Z
	</Dimension>
	<Dimension name="elevation" default="200.0" units="EPSG:5030" unitSymbol="m">
		200.0,300.0,500.0,600.0,700.0,850.0,925.0,1000.0
	</Dimension>

The table on postgres
"""""""""""""""""""""

With the elevation support enabled the table on postgres has, for each image, the field **elevation** filled with the elevation value.

.. figure:: images/elevationTable.png
   :align: center


.. note:: The user must create manually the index on the table in order to speed up the search by attribute.


Query layer on timestamp: 
`````````````````````````````````

In order to display a snapshot of the map at a specific time instant and elevation you have to pass in the request those parameters.

* **&time=** < **pattern** > , as shown before,

* **&elevation=** < **pattern** > where you pass the value of the elevation.

For example if an user wants to obtain the temperature coverage images for the day **2013-03-10 at 6 PM** at elevation **200 meters** must append to the request::

&time=2013-03-10T00:00:00.000Z&elevation=200.0

.. figure:: images/temperature1.png
   :align: center
   
Same day at elevation **300.0 meters**::
   
&time=2013-03-10T00:00:00.000Z&elevation=300.0

.. figure:: images/temperature2.png
   :align: center

Note that if just the time dimension is append to the request will be displayed the elevation **200 meters** (if present) because of the **default** attribute of the tag ``<Dimension name="elevation" ...`` in the WMS Capabilities document is set to **200**
   
   
   
   
   
   
   
